package com.example.assignment2;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.net.URI;
import java.util.Locale;

public class HelloApplication extends Application {

    private static final String APP_BACKGROUND = "#101014";
    private static final String PANEL_BACKGROUND = "#17171D";
    private static final String PANEL_BORDER = "#2A2A32";
    private static final String ACCENT = "#A879FF";
    private static final String ACCENT_SOFT = "#C9A8FF";
    private static final String TEXT_PRIMARY = "#F4F4F6";
    private static final String TEXT_SECONDARY = "#B0B0B8";
    private static final String BUTTON_MUTED = "#22242B";
    private static final String VIDEO_ACCENT = "#FF6B9D";
    private static final String VIDEO_ACCENT_SOFT = "#FF9EC0";

    private static final double MIN_WINDOW_WIDTH = 980;
    private static final double MIN_WINDOW_HEIGHT = 700;
    private static final double DEFAULT_VOLUME = 0.5;
    private static final double VOLUME_STEP = 0.1;

    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private ListView<File> playlistView;
    private int currentIndex = -1;

    private boolean isMuted = false;
    private double lastVolume = DEFAULT_VOLUME;

    private Label songTitleLabel;
    private Label statusLabel;
    private Slider progressSlider;
    private Slider volumeSlider;
    private Label timeLabel;
    private Label durationLabel;
    private Button muteButton;

    // Animated background elements shown during audio playback
    private Rectangle musicBackground;
    private Rectangle accentGlow;
    private Timeline glowTimeline;

    @Override
    public void start(Stage primaryStage) {
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.setSmooth(true);
        mediaView.setFitWidth(640);
        mediaView.setFitHeight(420);

        // ===== Animated music background (only visible for audio) =====
        musicBackground = new Rectangle();
        musicBackground.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1A0B2E")),
                new Stop(0.35, Color.web("#2D1B4E")),
                new Stop(0.65, Color.web("#1E3A5F")),
                new Stop(1, Color.web("#0D1B2A"))
        ));
        musicBackground.setOpacity(0.0);
        musicBackground.setManaged(false);

        accentGlow = new Rectangle();
        accentGlow.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(ACCENT, 0.35)),
                new Stop(0.5, Color.web("#5B8DEF", 0.18)),
                new Stop(1, Color.web("#FF6B9D", 0.10))
        ));
        accentGlow.setOpacity(0.0);
        accentGlow.setManaged(false);

        // Stack holds the backgrounds behind the MediaView
        StackPane mediaStack = new StackPane(musicBackground, accentGlow, mediaView);
        mediaStack.setPrefSize(640, 420);
        mediaStack.setMinSize(360, 240);
        mediaStack.getStyleClass().add("media-pane");
        mediaStack.setStyle("-fx-background-color: transparent;");

        // Keep background and video sized to the pane
        musicBackground.widthProperty().bind(mediaStack.widthProperty());
        musicBackground.heightProperty().bind(mediaStack.heightProperty());
        accentGlow.widthProperty().bind(mediaStack.widthProperty());
        accentGlow.heightProperty().bind(mediaStack.heightProperty());
        mediaView.fitWidthProperty().bind(mediaStack.widthProperty());
        mediaView.fitHeightProperty().bind(mediaStack.heightProperty());

        songTitleLabel = new Label("No track selected");
        songTitleLabel.getStyleClass().add("song-title");

        statusLabel = new Label("READY");
        statusLabel.getStyleClass().add("status-label");

        VBox mediaInfoBox = new VBox(6, songTitleLabel, statusLabel);
        mediaInfoBox.setAlignment(Pos.CENTER_LEFT);
        mediaInfoBox.setPadding(new Insets(8, 0, 0, 0));

        Label libraryTitle = new Label("YOUR LIBRARY");
        libraryTitle.getStyleClass().add("library-title");

        playlistView = new ListView<>(playlist);
        playlistView.setPlaceholder(new Label("No music yet"));
        playlistView.getStyleClass().add("playlist-view");

        playlistView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("playlist-item-selected");
                    setStyle("");
                    return;
                }

                setText(item.getName());
                setGraphic(null);
                setAlignment(Pos.CENTER_LEFT);
                setPadding(new Insets(0, 10, 0, 10));

                if (isSelected()) {
                    getStyleClass().add("playlist-item-selected");
                } else {
                    getStyleClass().remove("playlist-item-selected");
                }
            }
        });

        playlistView.getSelectionModel().selectedIndexProperty().addListener((obs, oldValue, newValue) -> {
            int index = newValue.intValue();
            if (index >= 0 && index < playlist.size() && index != currentIndex) {
                playMedia(index);
            }
        });

        Button btnAdd = createActionButton("＋ Add Music", ACCENT, ACCENT_SOFT);
        Button btnAddVideo = createActionButton("＋ Add Video", VIDEO_ACCENT, VIDEO_ACCENT_SOFT);
        Button btnRemove = createActionButton("Remove", BUTTON_MUTED, "#2C2E36");

        btnAdd.setOnAction(e -> addMusicFiles(primaryStage));
        btnAddVideo.setOnAction(e -> addVideoFiles(primaryStage));
        btnRemove.setOnAction(e -> removeSelectedTrack());

        HBox libraryActions = new HBox(8, btnAdd, btnAddVideo, btnRemove);
        libraryActions.setAlignment(Pos.CENTER_LEFT);

        VBox libraryPane = new VBox(12, libraryTitle, playlistView, libraryActions);
        libraryPane.setPadding(new Insets(18));
        libraryPane.setPrefWidth(300);
        libraryPane.setMinWidth(250);
        libraryPane.getStyleClass().add("sidebar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label appIcon = new Label("♫");
        appIcon.getStyleClass().add("app-icon");

        VBox titleBox = new VBox(2);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label appTitle = new Label("MUSICPLAYER");
        appTitle.getStyleClass().add("app-title");

        Label appSubtitle = new Label("YOUR MUSIC. YOUR STYLE.");
        appSubtitle.getStyleClass().add("app-subtitle");

        titleBox.getChildren().addAll(appTitle, appSubtitle);

        HBox topBar = new HBox(12, appIcon, titleBox, spacer, btnAdd, btnAddVideo);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(18, 22, 12, 22));
        topBar.getStyleClass().add("top-bar");

        Button btnPrev = createControlButton("⏮");
        Button btnPlay = createControlButton("▶");
        Button btnPause = createControlButton("⏸");
        Button btnStop = createControlButton("■");
        Button btnNext = createControlButton("⏭");

        btnPrev.setOnAction(e -> playPrevious());
        btnPlay.setOnAction(e -> togglePlayPause());
        btnPause.setOnAction(e -> pauseMedia());
        btnStop.setOnAction(e -> stopMedia());
        btnNext.setOnAction(e -> playNext());

        progressSlider = new Slider(0, 100, 0);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        progressSlider.getStyleClass().add("progress-slider");
        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && mediaPlayer != null) {
                double totalMillis = mediaPlayer.getTotalDuration().toMillis();
                if (totalMillis > 0) {
                    seekTo((progressSlider.getValue() / 100.0) * totalMillis);
                }
            }
        });

        timeLabel = new Label("00:00");
        timeLabel.getStyleClass().add("time-label");

        durationLabel = new Label("00:00");
        durationLabel.getStyleClass().add("time-label");

        HBox progressRow = new HBox(12, timeLabel, progressSlider, durationLabel);
        progressRow.setAlignment(Pos.CENTER);
        progressRow.setPadding(new Insets(8, 0, 0, 0));
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        volumeSlider = new Slider(0, 1, DEFAULT_VOLUME);
        volumeSlider.setPrefWidth(160);
        volumeSlider.setMinWidth(120);
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newValue.doubleValue());
                isMuted = newValue.doubleValue() == 0.0;
                if (mediaPlayer.getVolume() > 0.0) {
                    lastVolume = mediaPlayer.getVolume();
                }
            }
        });

        muteButton = createControlButton("🔇");
        muteButton.setOnAction(e -> toggleMute());

        HBox volumeRow = new HBox(12, muteButton, volumeSlider);
        volumeRow.setAlignment(Pos.CENTER_RIGHT);

        HBox controlsRow = new HBox(18, btnPrev, btnPlay, btnPause, btnStop, btnNext);
        controlsRow.setAlignment(Pos.CENTER);
        controlsRow.setPadding(new Insets(8, 0, 0, 0));

        VBox centerContent = new VBox(8, mediaStack, mediaInfoBox, progressRow, controlsRow, volumeRow);
        centerContent.setPadding(new Insets(0, 10, 16, 10));
        centerContent.setAlignment(Pos.CENTER);
        centerContent.setFillWidth(true);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(centerContent);
        root.setRight(libraryPane);
        root.setPadding(new Insets(0));
        root.getStyleClass().add("root-pane");

        Scene scene = new Scene(root, 1100, 720);
        scene.setFill(Color.web(APP_BACKGROUND));

        var cssUrl = getClass().getResource("/blackplayer.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        scene.setOnKeyPressed(this::handleKeyPress);

        primaryStage.setScene(scene);
        primaryStage.setTitle("BLACKPLAYER");
        primaryStage.setMinWidth(MIN_WINDOW_WIDTH);
        primaryStage.setMinHeight(MIN_WINDOW_HEIGHT);
        primaryStage.show();
    }

    private Button createActionButton(String text, String baseColor, String hoverColor) {
        Button button = new Button(text);
        button.getStyleClass().add("action-button");
        button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: transparent;",
                baseColor, TEXT_PRIMARY
        ));

        button.setOnMouseEntered(e -> button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: transparent;",
                hoverColor, TEXT_PRIMARY
        )));

        button.setOnMouseExited(e -> button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: transparent;",
                baseColor, TEXT_PRIMARY
        )));

        return button;
    }

    private Button createControlButton(String symbol) {
        Button button = new Button(symbol);
        button.getStyleClass().add("control-button");
        return button;
    }

    private void addMusicFiles(Stage primaryStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add music");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Audio Files",
                        "*.mp3", "*.wav", "*.m4a", "*.aac", "*.flac", "*.ogg"
                )
        );
        addFileFromChooser(fileChooser, primaryStage);
    }

    private void addVideoFiles(Stage primaryStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add video");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Video Files",
                        "*.mp4", "*.mkv", "*.mov", "*.avi", "*.wmv", "*.flv", "*.webm"
                )
        );
        addFileFromChooser(fileChooser, primaryStage);
    }

    private void addFileFromChooser(FileChooser fileChooser, Stage primaryStage) {
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile == null) {
            return;
        }

        if (playlist.contains(selectedFile)) {
            return;
        }

        playlist.add(selectedFile);
        playlistView.getSelectionModel().select(playlist.size() - 1);

        if (currentIndex == -1) {
            playMedia(0);
        }
    }

    private void removeSelectedTrack() {
        int selectedIndex = playlistView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        playlist.remove(selectedIndex);

        if (playlist.isEmpty()) {
            stopMedia();
            currentIndex = -1;
            songTitleLabel.setText("No track selected");
            statusLabel.setText("READY");
            timeLabel.setText("00:00");
            durationLabel.setText("00:00");
            progressSlider.setValue(0);
            hideMusicBackground();
            return;
        }

        if (selectedIndex == currentIndex) {
            playMedia(Math.min(selectedIndex, playlist.size() - 1));
        } else if (selectedIndex < currentIndex) {
            currentIndex--;
        }

        if (playlist.size() > 0) {
            playlistView.getSelectionModel().select(Math.min(selectedIndex, playlist.size() - 1));
        }
    }

    private void playMedia(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        stopCurrentPlayer();

        currentIndex = index;
        File selectedFile = playlist.get(index);
        playlistView.getSelectionModel().select(index);

        boolean audioOnly = isAudioFile(selectedFile);

        try {
            URI uri = selectedFile.toURI();
            Media media = new Media(uri.toString());

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnReady(() -> {
                updateProgressDisplay();
                // Show the background only for audio-only files
                if (audioOnly) {
                    showMusicBackground();
                    startGlowAnimation();
                } else {
                    hideMusicBackground();
                }
            });
            mediaPlayer.setOnPlaying(() -> {
                statusLabel.setText("PLAYING");
                if (audioOnly) {
                    startGlowAnimation();
                }
            });
            mediaPlayer.setOnPaused(() -> statusLabel.setText("PAUSED"));
            mediaPlayer.setOnStopped(() -> statusLabel.setText("STOPPED"));
            mediaPlayer.setOnEndOfMedia(this::playNext);
            mediaPlayer.setOnError(() -> {
                statusLabel.setText("ERROR");
                songTitleLabel.setText("Unable to play file");
            });

            // CRITICAL: Attach the player to the MediaView so video is displayed.
            mediaView.setMediaPlayer(mediaPlayer);
            mediaView.setVisible(true);
            mediaView.toFront();
            mediaView.setOpacity(1.0);

            mediaPlayer.setVolume(volumeSlider.getValue());
            mediaPlayer.play();

            updateSongInfo(selectedFile.getName());
            statusLabel.setText("PLAYING");
        } catch (MediaException | IllegalArgumentException e) {
            statusLabel.setText("ERROR");
            songTitleLabel.setText("Invalid media file");
        }
    }

    /** Determines whether the file is audio-only based on its extension. */
    private boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".wav")
                || name.endsWith(".m4a") || name.endsWith(".aac")
                || name.endsWith(".flac") || name.endsWith(".ogg");
    }

    /** Fade in the animated music background behind the audio. */
    private void showMusicBackground() {
        FadeTransition fade = new FadeTransition(Duration.millis(600), musicBackground);
        fade.setFromValue(musicBackground.getOpacity());
        fade.setToValue(1.0);
        fade.play();
    }

    /** Fade out the music background (e.g. when a video is playing). */
    private void hideMusicBackground() {
        FadeTransition fade = new FadeTransition(Duration.millis(400), musicBackground);
        fade.setFromValue(musicBackground.getOpacity());
        fade.setToValue(0.0);
        fade.play();

        if (glowTimeline != null) {
            glowTimeline.stop();
            glowTimeline = null;
        }

        FadeTransition glowFade = new FadeTransition(Duration.millis(400), accentGlow);
        glowFade.setFromValue(accentGlow.getOpacity());
        glowFade.setToValue(0.0);
        glowFade.play();
    }

    /** Pulsing glow animation while music is playing. */
    private void startGlowAnimation() {
        if (glowTimeline != null) {
            glowTimeline.stop();
        }
        glowTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(accentGlow.opacityProperty(), 0.0)),
                new KeyFrame(Duration.seconds(2), new KeyValue(accentGlow.opacityProperty(), 1.0)),
                new KeyFrame(Duration.seconds(4), new KeyValue(accentGlow.opacityProperty(), 0.0))
        );
        glowTimeline.setCycleCount(Timeline.INDEFINITE);
        glowTimeline.play();
    }

    private void stopCurrentPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        mediaView.setMediaPlayer(null);
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            if (!playlist.isEmpty()) {
                playMedia(0);
            }
            return;
        }

        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
            statusLabel.setText("PLAYING");
            if (musicBackground.getOpacity() > 0.1) {
                startGlowAnimation();
            }
        }
    }

    private void pauseMedia() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        }
    }

    private void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
            statusLabel.setText("STOPPED");
        }
        mediaView.setMediaPlayer(null);
        hideMusicBackground();
    }

    private void playNext() {
        if (playlist.isEmpty()) {
            return;
        }

        if (currentIndex < playlist.size() - 1) {
            playMedia(currentIndex + 1);
        } else {
            playMedia(0);
        }
    }

    private void playPrevious() {
        if (playlist.isEmpty()) {
            return;
        }

        if (currentIndex > 0) {
            playMedia(currentIndex - 1);
        } else {
            playMedia(playlist.size() - 1);
        }
    }

    private void toggleMute() {
        if (mediaPlayer == null) {
            return;
        }

        if (isMuted) {
            mediaPlayer.setVolume(lastVolume);
            volumeSlider.setValue(lastVolume);
            isMuted = false;
            muteButton.setText("🔇");
        } else {
            lastVolume = mediaPlayer.getVolume();
            mediaPlayer.setVolume(0.0);
            volumeSlider.setValue(0.0);
            isMuted = true;
            muteButton.setText("🔊");
        }
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            event.consume();
            togglePlayPause();
            return;
        }

        if (mediaPlayer == null) {
            return;
        }

        switch (event.getCode()) {
            case N:
                event.consume();
                playNext();
                break;
            case P:
                event.consume();
                playPrevious();
                break;
            case S:
                event.consume();
                stopMedia();
                break;
            case M:
                event.consume();
                toggleMute();
                break;
            case UP:
                event.consume();
                increaseVolume();
                break;
            case DOWN:
                event.consume();
                decreaseVolume();
                break;
            default:
                break;
        }
    }

    private void increaseVolume() {
        if (mediaPlayer == null) {
            return;
        }

        double nextVolume = Math.min(1.0, mediaPlayer.getVolume() + VOLUME_STEP);
        mediaPlayer.setVolume(nextVolume);
        volumeSlider.setValue(nextVolume);
        isMuted = nextVolume == 0.0;
        if (nextVolume > 0.0) {
            lastVolume = nextVolume;
        }
    }

    private void decreaseVolume() {
        if (mediaPlayer == null) {
            return;
        }

        double nextVolume = Math.max(0.0, mediaPlayer.getVolume() - VOLUME_STEP);
        mediaPlayer.setVolume(nextVolume);
        volumeSlider.setValue(nextVolume);
        isMuted = nextVolume == 0.0;
        if (nextVolume > 0.0) {
            lastVolume = nextVolume;
        }
    }

    private void seekTo(double milliseconds) {
        if (mediaPlayer == null) {
            return;
        }

        double total = mediaPlayer.getTotalDuration().toMillis();
        if (total <= 0) {
            return;
        }

        double safeValue = Math.max(0, Math.min(milliseconds, total));
        mediaPlayer.seek(Duration.millis(safeValue));
    }

    private void updateSongInfo(String title) {
        if (title == null || title.isBlank()) {
            songTitleLabel.setText("No track selected");
            return;
        }

        songTitleLabel.setText(title);
    }

    private void updateProgressDisplay() {
        if (mediaPlayer == null) {
            return;
        }

        Duration total = mediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown()) {
            total = Duration.ZERO;
        }

        durationLabel.setText(formatDuration(total));

        mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
            if (mediaPlayer == null) {
                return;
            }

            Duration current = mediaPlayer.getCurrentTime();
            Duration totalDuration = mediaPlayer.getTotalDuration();

            if (totalDuration == null || totalDuration.isUnknown()) {
                progressSlider.setValue(0);
                timeLabel.setText(formatDuration(current));
                durationLabel.setText(formatDuration(current));
                return;
            }

            double percentage = (current.toMillis() / totalDuration.toMillis()) * 100.0;
            if (!progressSlider.isValueChanging()) {
                progressSlider.setValue(Math.max(0, Math.min(100, percentage)));
            }

            timeLabel.setText(formatDuration(current));
            durationLabel.setText(formatDuration(totalDuration));
        });
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "00:00";
        }

        long totalSeconds = (long) Math.floor(duration.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static void main(String[] args) {
        launch(args);
    }
}