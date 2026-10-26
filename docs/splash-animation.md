# Super splash animation

The bundled `res/raw/launcher_en.gif` and `launcher_zh.gif` retain the original
1920×1080 transparent canvas, original logo animation and 1350 ms total duration.
Five red SUPER letters enter at two-frame intervals, rising from their baseline
and settling with a small overshoot alongside the original GAMEHUB word.
SUPER capitals are 183 px tall versus 122 px for the original capitals.
The complete composition is vertically recentered without cropping the source.

`activity_splash.xml` doubles the centered image view from 266×150 dp to
532×300 dp. SplashActivity is landscape-only. The original Glide playback,
single-loop setting and completion callback are unchanged.

The approved icon was the reference for an image-generated isolated red wordmark.
The committed `super-wordmark.png` retains the five cleaned glyph silhouettes.
Regenerate GIFs and previews with `python tools/create-splash-animation.py
path/to/decoded/source/apk` (Pillow, NumPy and SciPy required for this maintainer
tool; CI and Android use the committed GIF assets directly).

The script checks both GIFs' decoded dimensions and total duration. Device
appearance still requires on-phone review. `assets/branding/splash-preview.gif`
and `splash-final.png` preview the composition; the actual 2× display size is
controlled by the layout patch.
