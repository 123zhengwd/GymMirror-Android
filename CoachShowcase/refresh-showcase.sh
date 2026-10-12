#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SOURCE=${COACH_SHOWCASE_H5_SOURCE:-"$PROJECT_ROOT/../nfc-h5"}
if [ ! -d "$SOURCE" ]; then
  SOURCE='/Users/kun/Documents/ChatGPT/互动大屏/乐堤港大屏预览/app/nfc-h5'
fi
OUTPUT="$PROJECT_ROOT/app/src/main/assets/site"

cd "$SOURCE"
./node_modules/.bin/vite build --base=./ --outDir "$OUTPUT" --emptyOutDir

# This 4K source is shown inside a much smaller portrait card. Keep its
# playback but reduce the APK size before packaging.
VIDEO=$(find "$OUTPUT/assets" -maxdepth 1 -name 'leg-curl-tutorial.*.mp4' -print -quit)
if [ -n "$VIDEO" ]; then
  ffmpeg -hide_banner -loglevel error -i "$VIDEO" \
    -vf scale=1280:-2 -c:v libx264 -preset veryfast -crf 23 \
    -c:a aac -b:a 128k -movflags +faststart "$VIDEO.optimized.mp4"
  mv "$VIDEO.optimized.mp4" "$VIDEO"
fi
