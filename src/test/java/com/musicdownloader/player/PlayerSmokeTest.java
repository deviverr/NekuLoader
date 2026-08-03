package com.musicdownloader.player;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PlayerSmokeTest {
    private PlayerSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        File audioFile = new File(args.length > 0 ? args[0] : "/tmp/nekuloader-player-test.wav");
        TrackMetadata metadata = TrackMetadata.read(audioFile);
        require("NekuLoaderTest".equals(metadata.title()), "title metadata");
        require("deviver".equals(metadata.artist()), "artist metadata");
        require("PlayerSmoke".equals(metadata.album()), "album metadata");
        require(metadata.durationSeconds() > 0.8, "duration metadata");

        if (args.length < 2 || !"--playback".equals(args[1])) {
            System.out.println("Player metadata smoke test passed");
            return;
        }

        CountDownLatch completed = new CountDownLatch(1);
        try (AudioPlayer player = new AudioPlayer()) {
            player.setListener(new AudioPlayer.Listener() {
                @Override
                public void onCompleted(File file) {
                    completed.countDown();
                }

                @Override
                public void onError(File file, String message) {
                    throw new AssertionError(message);
                }
            });
            player.setVolume(0.0f);
            player.play(audioFile);
            require(completed.await(5, TimeUnit.SECONDS), "playback completion");
        }
        System.out.println("Player playback smoke test passed");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}
