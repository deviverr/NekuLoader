#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

./build.sh

TEST_CLASSES="build/test-classes"
FIXTURE="/tmp/nekuloader-player-test.wav"

rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

find src/test/java -type f -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -cp 'build/classes:lib/*' -d "$TEST_CLASSES"
find "$TEST_CLASSES" -name '._*' -type f -delete

ffmpeg -y -v error -f lavfi -i sine=frequency=440:duration=1 \
  -metadata title=NekuLoaderTest \
  -metadata artist=deviver \
  -metadata album=PlayerSmoke \
  "$FIXTURE"

java -cp "$TEST_CLASSES:build/classes:lib/*" com.musicdownloader.downloader.ProviderCapabilitiesTest
java -cp "$TEST_CLASSES:build/classes:lib/*" com.musicdownloader.utils.DownloadHistoryStoreTest
java -cp "$TEST_CLASSES:build/classes:lib/*" com.musicdownloader.utils.ToolLocatorTest
java -cp "$TEST_CLASSES:build/classes:lib/*" com.musicdownloader.player.PlayerSmokeTest "$FIXTURE"

export NEKULOADER_ADB="$PWD/src/test/resources/fake-adb.sh"
export FAKE_ADB_LOG="/tmp/nekuloader-fake-adb.log"
rm -f "$FAKE_ADB_LOG"
java -cp "$TEST_CLASSES:build/classes:lib/*" com.musicdownloader.downloader.DeviceTransferManagerTest

if [ "${RUN_AUDIO_PLAYBACK_TEST:-0}" = "1" ]; then
  java -cp "$TEST_CLASSES:build/classes:lib/*" com.musicdownloader.player.PlayerSmokeTest "$FIXTURE" --playback
fi

find . -name '._*' -type f -delete

echo "All tests passed"
