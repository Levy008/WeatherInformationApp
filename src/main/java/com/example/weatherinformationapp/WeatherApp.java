package com.example.weatherinformationapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weather Information App - JavaFX Implementation
 * Features: Real-time OpenWeatherMap API integration, unit conversion (°C/°F),
 * dynamic time-based backgrounds, forecast display, input validation, and search history.
 */
public class WeatherApp extends Application {

    // OpenWeatherMap API Configuration
    private static final String API_KEY = "7dc720afeec821c95e546a1b58369c01";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";

    // UI Components
    private BorderPane root;
    private TextField cityInputField;
    private RadioButton celsiusRadio;
    private Label cityNameLabel;
    private Label tempLabel;
    private Label conditionLabel;
    private Label humidityLabel;
    private Label windLabel;
    private ImageView weatherIconView;
    private HBox forecastContainer;
    private ObservableList<String> searchHistory;

    // HTTP Client for API calls
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Weather Information App");

        // Root Layout
        root = new BorderPane();
        root.setPadding(new Insets(15));

        // 1. TOP REGION: Search Bar & Unit Toggle
        HBox topBar = createTopBar();
        root.setTop(topBar);

        // 2. CENTER REGION: Main Weather Display & Forecast
        VBox centerContainer = createCenterContainer();
        root.setCenter(centerContainer);

        // 3. BOTTOM REGION: Search History Log
        VBox bottomContainer = createBottomContainer();
        root.setBottom(bottomContainer);

        // Apply Dynamic Background based on current time of day
        applyDynamicBackground();

        // Create Scene and Show Stage
        Scene scene = new Scene(root, 700, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Constructs the Top Input Bar (City Field, Search Button, Unit Toggle)
     */
    private HBox createTopBar() {
        HBox topBox = new HBox(12);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(10, 0, 15, 0));

        cityInputField = new TextField();
        cityInputField.setPromptText("Enter city name (e.g. London, Harare)");
        cityInputField.setPrefWidth(260);

        Button searchBtn = new Button("Search Weather");
        searchBtn.setStyle("-fx-background-color: #2b5c8f; -fx-text-fill: white; -fx-font-weight: bold;");
        searchBtn.setOnAction(e -> fetchWeatherData());

        // Toggle Group for Units (°C vs °F)
        ToggleGroup unitGroup = new ToggleGroup();
        celsiusRadio = new RadioButton("°C (Metric)");
        celsiusRadio.setToggleGroup(unitGroup);
        celsiusRadio.setSelected(true);

        RadioButton fahrenheitRadio = new RadioButton("°F (Imperial)");
        fahrenheitRadio.setToggleGroup(unitGroup);

        // Re-fetch weather when units change if a city is already displayed
        unitGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (!cityNameLabel.getText().isEmpty() && !cityNameLabel.getText().equals("City Name")) {
                fetchWeatherData();
            }
        });

        topBox.getChildren().addAll(cityInputField, searchBtn, celsiusRadio, fahrenheitRadio);
        return topBox;
    }

    /**
     * Constructs the Center Display Area (Current Conditions & Forecast)
     */
    private VBox createCenterContainer() {
        VBox centerBox = new VBox(15);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(20));
        centerBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.75); -fx-background-radius: 12;");

        cityNameLabel = new Label("City Name");
        cityNameLabel.setFont(Font.font("System", FontWeight.BOLD, 26));

        weatherIconView = new ImageView();
        weatherIconView.setFitWidth(100);
        weatherIconView.setFitHeight(100);

        tempLabel = new Label("-- °C");
        tempLabel.setFont(Font.font("System", FontWeight.BOLD, 36));

        conditionLabel = new Label("Enter a city to display conditions");
        conditionLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));

        HBox metricsBox = new HBox(30);
        metricsBox.setAlignment(Pos.CENTER);
        humidityLabel = new Label("Humidity: --%");
        windLabel = new Label("Wind Speed: -- m/s");
        metricsBox.getChildren().addAll(humidityLabel, windLabel);

        // Forecast Section Title and Container
        Label forecastTitle = new Label("Short-Term Forecast");
        forecastTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        forecastContainer = new HBox(15);
        forecastContainer.setAlignment(Pos.CENTER);

        centerBox.getChildren().addAll(
                cityNameLabel, weatherIconView, tempLabel, conditionLabel,
                metricsBox, new Separator(), forecastTitle, forecastContainer
        );
        return centerBox;
    }

    /**
     * Constructs the Bottom Search History Container
     */
    private VBox createBottomContainer() {
        VBox bottomBox = new VBox(8);
        bottomBox.setPadding(new Insets(15, 0, 0, 0));

        Label historyTitle = new Label("Recent Search History");
        historyTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        searchHistory = FXCollections.observableArrayList();
        ListView<String> historyListView = new ListView<>(searchHistory);
        historyListView.setPrefHeight(100);

        bottomBox.getChildren().addAll(historyTitle, historyListView);
        return bottomBox;
    }

    /**
     * Fetches current weather and forecast data from OpenWeatherMap API
     */
    private void fetchWeatherData() {
        String city = cityInputField.getText().trim();

        // Input Validation
        if (city.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Error", "Please enter a valid city name.");
            return;
        }

        String unitParam = celsiusRadio.isSelected() ? "metric" : "imperial";
        String currentUrl = String.format("%s?q=%s&appid=%s&units=%s", WEATHER_URL, city.replace(" ", "%20"), API_KEY, unitParam);
        String forecastUrl = String.format("%s?q=%s&appid=%s&units=%s", FORECAST_URL, city.replace(" ", "%20"), API_KEY, unitParam);

        // Execute API calls asynchronously
        httpClient.sendAsync(
                HttpRequest.newBuilder().uri(URI.create(currentUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                Platform.runLater(() -> parseAndDisplayCurrentWeather(response.body(), city));
                // Fetch forecast only if current weather succeeded
                fetchForecastData(forecastUrl);
            } else if (response.statusCode() == 404) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "City Not Found", "The city '" + city + "' could not be found."));
            } else {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "API Error", "Error fetching data. HTTP Code: " + response.statusCode()));
            }
        }).exceptionally(ex -> {
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Network Error", "Failed to connect to the weather service. Check your internet connection."));
            return null;
        });
    }

    /**
     * Fetches forecast data from API
     */
    private void fetchForecastData(String forecastUrl) {
        httpClient.sendAsync(
                HttpRequest.newBuilder().uri(URI.create(forecastUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                Platform.runLater(() -> parseAndDisplayForecast(response.body()));
            }
        });
    }

    /**
     * Parses Current Weather JSON response and updates GUI
     */
    private void parseAndDisplayCurrentWeather(String json, String queryCity) {
        String name = extractJsonValue(json, "\"name\":\"(.*?)\"");
        String temp = extractJsonValue(json, "\"temp\":([0-9.-]+)");
        String humidity = extractJsonValue(json, "\"humidity\":([0-9]+)");
        String windSpeed = extractJsonValue(json, "\"speed\":([0-9.-]+)");
        String condition = extractJsonValue(json, "\"main\":\"(.*?)\"");
        String iconCode = extractJsonValue(json, "\"icon\":\"(.*?)\"");

        String unitSymbol = celsiusRadio.isSelected() ? "°C" : "°F";
        String speedUnit = celsiusRadio.isSelected() ? "m/s" : "mph";

        cityNameLabel.setText(name);
        tempLabel.setText(Math.round(Double.parseDouble(temp)) + " " + unitSymbol);
        conditionLabel.setText(condition);
        humidityLabel.setText("Humidity: " + humidity + "%");
        windLabel.setText("Wind Speed: " + windSpeed + " " + speedUnit);

        // Load Weather Icon from OpenWeatherMap CDN
        if (!iconCode.isEmpty()) {
            String iconUrl = "https://openweathermap.org/img/wn/" + iconCode + "@2x.png";
            weatherIconView.setImage(new Image(iconUrl, true));
        }

        // Add search event to History Log with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String historyEntry = String.format("[%s] %s: %s%s, %s", timestamp, name, Math.round(Double.parseDouble(temp)), unitSymbol, condition);
        searchHistory.add(0, historyEntry);

        applyDynamicBackground();
    }

    /**
     * Parses Forecast JSON and displays short-term forecast cards
     */
    private void parseAndDisplayForecast(String json) {
        forecastContainer.getChildren().clear();
        String unitSymbol = celsiusRadio.isSelected() ? "°C" : "°F";

        // Extract up to 3 forecast entries from the JSON list
        Pattern listPattern = Pattern.compile("\\{\"dt\":.*?\r?\n?.*?\"temp\":([0-9.-]+).*?\"icon\":\"(.*?)\"");
        Matcher matcher = listPattern.matcher(json);

        int count = 0;
        while (matcher.find() && count < 3) {
            String temp = matcher.group(1);
            String icon = matcher.group(2);

            VBox card = new VBox(5);
            card.setAlignment(Pos.CENTER);
            card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); -fx-padding: 8; -fx-background-radius: 8;");

            ImageView iconView = new ImageView(new Image("https://openweathermap.org/img/wn/" + icon + ".png", true));
            iconView.setFitWidth(40);
            iconView.setFitHeight(40);

            Label tempLbl = new Label(Math.round(Double.parseDouble(temp)) + unitSymbol);
            tempLbl.setFont(Font.font("System", FontWeight.BOLD, 12));

            card.getChildren().addAll(iconView, tempLbl);
            forecastContainer.getChildren().add(card);
            count++;
        }
    }

    /**
     * Applies dynamic CSS background gradients based on local time of day
     */
    private void applyDynamicBackground() {
        LocalTime now = LocalTime.now();
        String gradientCss;

        if (now.isAfter(LocalTime.of(6, 0)) && now.isBefore(LocalTime.of(17, 0))) {
            // Day Time: Light Sky Blue Gradient
            gradientCss = "-fx-background: linear-gradient(to bottom, #70e1f5, #ffd194);";
        } else if (now.isAfter(LocalTime.of(17, 0)) && now.isBefore(LocalTime.of(20, 0))) {
            // Evening / Sunset: Warm Orange/Purple Gradient
            gradientCss = "-fx-background: linear-gradient(to bottom, #2c3e50, #fd746c);";
        } else {
            // Night Time: Dark Navy/Blue Gradient
            gradientCss = "-fx-background: linear-gradient(to bottom, #0f2027, #203a43, #2c5364);";
        }

        if (root != null) {
            root.setStyle(gradientCss);
        }
    }

    /**
     * Regex Helper to extract JSON properties without external dependencies
     */
    private String extractJsonValue(String json, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Displays JavaFX Alert dialogs
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}