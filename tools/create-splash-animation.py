#!/usr/bin/env python3
"""Preserve the source splash timeline, adding five matching letter pops.

Maintainer tool: pip install pillow numpy scipy; provide the decoded source APK
directory. Android/CI consumes the committed GIFs without these dependencies.
"""
import argparse
from pathlib import Path
import numpy as np
from PIL import Image
from scipy.ndimage import label, find_objects

ROOT = Path(__file__).resolve().parents[1]


def build(source):
    word = Image.open(ROOT / 'assets/branding/super-wordmark.png').convert('RGBA')
    labels, count = label(np.array(word.getchannel('A')) > 127)
    regions = find_objects(labels)
    letters = []
    for index, region in enumerate(regions, 1):
        if np.count_nonzero(labels == index) < 100:
            continue
        y, x = region
        part = word.crop((x.start, y.start, x.stop, y.stop))
        alpha = np.where(labels[region] == index, 255, 0).astype('uint8')
        part.putalpha(Image.fromarray(alpha))
        letters.append((x.start, part))
    letters.sort(key=lambda item: item[0])
    assert len(letters) == 5, 'Expected five separate SUPER glyphs'
    scale = 183 / word.height  # Original GAMEHUB capitals are 122 px tall.
    palette = [0, 0, 0, 255, 255, 255, 255, 0, 0] + [0] * (768 - 9)
    for locale in ('en', 'zh'):
        original = Image.open(source / 'res/raw' / ('launcher_' + locale + '.gif'))
        frames, durations = [], []
        for number in range(original.n_frames):
            original.seek(number)
            durations.append(original.info.get('duration', 30))
            canvas = Image.new('RGBA', original.size)
            canvas.alpha_composite(original.convert('RGBA'), (0, 104))
            for glyph, (x, part) in enumerate(letters):
                # Same two-frame stagger as GAMEHUB; rise from baseline and
                # settle after four frames, alongside the original word.
                age = number - (18 + 2 * glyph)
                if age < 0:
                    continue
                vertical = (0.08, 0.52, 1.04, 1.0)[min(age, 3)]
                width = round(part.width * scale)
                height = max(1, round(part.height * scale * vertical))
                resized = part.resize((width, height), Image.Resampling.LANCZOS)
                canvas.alpha_composite(resized, (670 + round(x * scale), 492 - height))
            pixels = np.array(canvas)
            indexed = np.zeros(pixels.shape[:2], dtype='uint8')
            visible = pixels[:, :, 3] >= 128
            red = pixels[:, :, 0] > pixels[:, :, 1] + 30.0
            indexed[visible] = 1
            indexed[visible & red] = 2
            frame = Image.fromarray(indexed, 'P'); frame.putpalette(palette)
            frame.info['transparency'] = 0
            frames.append(frame)
        destination = ROOT / 'patches/binary_replacements/res/raw' / ('launcher_' + locale + '.gif')
        frames[0].save(destination, save_all=True, append_images=frames[1:],
                       duration=durations, loop=0, transparency=0, disposal=2, optimize=False)
        check = Image.open(destination)
        total = 0
        for i in range(check.n_frames):
            check.seek(i); total += check.info['duration']
        assert total == sum(durations) == 1350
        assert check.size == (1920, 1080)
        print(destination.relative_to(ROOT), total, 'ms')
        if locale == 'en':
            preview = []
            for frame in frames:
                background = Image.new('RGBA', frame.size, '#101010')
                background.alpha_composite(frame.convert('RGBA'))
                preview.append(background.resize((960, 540)).convert('RGB'))
            output = ROOT / 'assets/branding'
            preview[0].save(output / 'splash-preview.gif', save_all=True,
                            append_images=preview[1:], duration=durations, loop=0)
            preview[-1].save(output / 'splash-final.png')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('decoded_apk', type=Path)
    build(parser.parse_args().decoded_apk)
