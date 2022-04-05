package ru.nsu.fit.wheretogo.map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.nsu.fit.wheretogo.AccountActivity;
import ru.nsu.fit.wheretogo.FavouritesActivity;
import ru.nsu.fit.wheretogo.ForYouActivity;
import ru.nsu.fit.wheretogo.PlaceActivity;
import ru.nsu.fit.wheretogo.R;
import ru.nsu.fit.wheretogo.VisitedActivity;
import ru.nsu.fit.wheretogo.databinding.ActivityMapBinding;
import ru.nsu.fit.wheretogo.model.ClusterMarker;
import ru.nsu.fit.wheretogo.model.PlaceList;
import ru.nsu.fit.wheretogo.model.ServiceGenerator;
import ru.nsu.fit.wheretogo.model.ShowMapMode;
import ru.nsu.fit.wheretogo.model.entity.Category;
import ru.nsu.fit.wheretogo.model.entity.Place;
import ru.nsu.fit.wheretogo.model.entity.Score;
import ru.nsu.fit.wheretogo.model.service.CategoryService;
import ru.nsu.fit.wheretogo.model.service.PlaceListService;
import ru.nsu.fit.wheretogo.model.service.PlaceService;
import ru.nsu.fit.wheretogo.model.service.ScoreService;
import ru.nsu.fit.wheretogo.model.service.StayPointService;
import ru.nsu.fit.wheretogo.util.AuthorizationHelper;
import ru.nsu.fit.wheretogo.util.ClusterManagerRenderer;
import ru.nsu.fit.wheretogo.util.PictureLoader;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int HALF_OUR_SEC = 1800;

    private Map<Integer, CategoryNameChosen> categories;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient fusedLocationProviderClient;

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private final HandlerThread locationUpdateThread = new HandlerThread("RequestLocation");

    //Заглшуки для фиксирования stay-point-ов
    private long timeCounter = 0;

    //Default location (Novosibirsk Rechnoy Vokzal)
    private final LatLng defaultLocation = new LatLng(55.008883, 82.938344);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location lastKnownLocation;

    //vars for markers
    private final ArrayList<Place> places = new ArrayList<>();

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    //adding markers on map
    private ClusterManager<ClusterMarker> clusterManager;
    private ClusterManagerRenderer clusterManagerRenderer;
    private final ArrayList<ClusterMarker> clusterMarkers = new ArrayList<>();

    //current_selected_place
    private ClusterMarker selectedPlace;

    private GoogleMap map;

    private SlidingPaneLayout mapSlidingPane;
    private LinearLayout filtersLayout;

    //map show mode
    private ShowMapMode showMapMode;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getCategories(null, null);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        //Утсанавливаем режим отображения мест
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            showMapMode = ShowMapMode.ALL;
        } else {
            int mode = extras.getInt("show_map_mode");
            switch (mode) {
                case 0:
                    showMapMode = ShowMapMode.ALL;
                    break;
                case 1:
                    showMapMode = ShowMapMode.NEAREST;
                    break;
                case 2:
                    showMapMode = ShowMapMode.RECOMMENDED_VISITED;
                    break;
                case 3:
                    showMapMode = ShowMapMode.RECOMMENDED_SCORED;
                    break;
                case 4:
                    showMapMode = ShowMapMode.RECOMMENDED_USERS;
                    break;
            }
        }

        // Retrieve the content view that renders the map.
        ru.nsu.fit.wheretogo.databinding.ActivityMapBinding binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mapSlidingPane = findViewById(R.id.mapSlidingPane);
        mapSlidingPane.setLockMode(SlidingPaneLayout.LOCK_MODE_LOCKED_CLOSED);
        mapSlidingPane.addPanelSlideListener(new SlidingPaneLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(@NonNull View panel, float slideOffset) {
            }

            @Override
            public void onPanelOpened(@NonNull View panel) {
            }

            @Override
            public void onPanelClosed(@NonNull View panel) {
                sendPlacesRequest();
            }
        });
        filtersLayout = findViewById(R.id.filtersLayout);


        // Construct a PlacesClient
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        // The entry point to the Places API.
        Places.createClient(this);

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        //Configure updates of location
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(2 * 1000);//2 seconds

        locationCallback = new LocationCallback() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onLocationResult(LocationResult locationResult) {
                //Получаем текущее метосположение пользователя
                if (locationResult != null) {
                    //Сравниваем его с предыдущим местоположением
                    if(lastKnownLocation == null){
                        lastKnownLocation = locationResult.getLastLocation();
                    }
                    double distance = locationResult.getLastLocation().distanceTo(lastKnownLocation);
                    //Если расстояние между ними меньше 200м, то прибавляем к счетчику время
                    if(distance <= 200.00){
//                        System.out.println("inside 200 meters area");
                        timeCounter += 2;
                    }
                    else {
                        //Сбрасываем счетчик
                        timeCounter = 0;
                    }

                    //Обновляем местоположение
                    lastKnownLocation = locationResult.getLastLocation();

                    //Если счетчик переполнился, то мы нашли stay-point
                    if(timeCounter == HALF_OUR_SEC){
//                        System.out.println("New stay-point candidate!");
                        timeCounter = 0;

                        //Добавляем его в базу данных
                        Call<String> call = ServiceGenerator.createService(StayPointService.class)
                                .addStayPoint(
                                        lastKnownLocation.getLatitude(),
                                        lastKnownLocation.getLongitude());

                        call.enqueue(new Callback<String>() {
                            @Override
                            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
//                                System.out.println("Add new stay-point");
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

                            }
                        });

                    }

//                    System.out.println("NEW LOCATION:("
//                            + lastKnownLocation.getLatitude() + ","
//                            + lastKnownLocation.getLongitude() + ")");

//                    map.moveCamera(CameraUpdateFactory.newLatLng(
//                            new LatLng(lastKnownLocation.getLatitude(),
//                                    lastKnownLocation.getLongitude())));


                }
            }
        };

        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        //Buttons init
        ImageButton recommenderButton = (ImageButton) findViewById(R.id.for_you_btn);
        ImageButton favoritesButton = (ImageButton) findViewById(R.id.favorites_btn);
        ImageButton visitedButton = (ImageButton) findViewById(R.id.visited_btn_map);
        ImageButton filters = (ImageButton) findViewById(R.id.filters_btn);
        ImageButton userPrefs = (ImageButton) findViewById(R.id.user_settings_btn);

        //Buttons listeners
        recommenderButton.setOnClickListener(this::openRecommenders);
        userPrefs.setOnClickListener(this::openUserPrefs);
        favoritesButton.setOnClickListener(this::openFavourites);
        visitedButton.setOnClickListener(this::openVisited);
        filters.setOnClickListener(this::openFilters);
    }


    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.map = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        this.map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(@NonNull Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(@NonNull Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.map), false);

                TextView title = infoWindow.findViewById(R.id.title);
                title.setText(marker.getTitle());

                TextView snippet = infoWindow.findViewById(R.id.snippet);
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();
    }

    // Отправляет запрос на получение мест,
    // там же происходит вызов отображения маркеров при получении ответа
    //Дополнительно сделан отбор мест, расположенных близко к пользователю, на основе его координат(решили не использовать при открытии экрана)
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void sendPlacesRequest() {
        PlaceListService placeService = ServiceGenerator.createService(PlaceListService.class);
        if (categories.values().stream().noneMatch(categoryNameChosen -> categoryNameChosen.chosen)) {
            categories.forEach((id, categoryNameChosen) -> categoryNameChosen.chosen = true);
        }

        //В зависимости от режима отображения будет загружен тот или иной список мест
        Call<PlaceList> placeCall;
        switch (showMapMode) {
            case NEAREST:
                placeCall = placeService.getNearestPlacesByCategory(
                        lastKnownLocation.getLatitude(),
                        lastKnownLocation.getLongitude(),
                        1.0,
                        2.0,
                        5,
                        categories.entrySet().stream()
                                .filter(entry -> entry.getValue().chosen)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()),
                        1, 0);
                break;
            case RECOMMENDED_VISITED:
                placeCall = placeService.getRecommendByVisited(
                        AuthorizationHelper.getUserProfile().getId(),
                        1,
                        0
                );
                break;
            case RECOMMENDED_SCORED:
                placeCall = placeService.getRecommendByScores(
                      AuthorizationHelper.getUserProfile().getId(),
                      1,
                      0
                );
                break;
            case RECOMMENDED_USERS:
                placeCall = placeService.getRecommendByUsers(
                        1,
                        0
                );
                break;
            default:
                placeCall = placeService.getPlaceList(
                        categories.entrySet().stream()
                                .filter(entry -> entry.getValue().chosen)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()),
                        1, 0);
                break;
        }

        placeCall.enqueue(new Callback<PlaceList>() {
            @Override
            public void onResponse(@NonNull Call<PlaceList> call,
                                   @NonNull Response<PlaceList> response) {
                PlaceList placeList = response.body();
                places.clear();
                if (placeList != null) {
                    places.addAll(placeList.getList());
                }
                updateMapMarkers();
            }

            @Override
            public void onFailure(@NonNull Call<PlaceList> call, @NonNull Throwable t) {
                System.out.println(t.getMessage());
            }
        });
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Set the map's camera position to the current location of the device.
                        lastKnownLocation = task.getResult();
                        if (lastKnownLocation != null) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(lastKnownLocation.getLatitude(),
                                            lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            sendPlacesRequest();
                            locationUpdateThread.start();
                        }
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        Log.e(TAG, "Exception: %s", task.getException());
                        map.moveCamera(CameraUpdateFactory
                                .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                        map.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            //Set loop to update location
//            locationUpdateThread.start();
            fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    locationUpdateThread.getLooper());
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        locationPermissionGranted = false;
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
                updateLocationUI();
            }
        }
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (map == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                map.setMyLocationEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    private void updateMapMarkers() {
        if (map != null) {
            if (clusterManager == null) {
                clusterManager = new ClusterManager<>(this.getApplicationContext(), map);
                clusterManager.setOnClusterItemClickListener(item -> {
                    //Выбранное место
                    selectedPlace = item;
                    openShortPlaceInfo();
                    return false;
                });
            }
            if (clusterManagerRenderer == null) {
                clusterManagerRenderer = new ClusterManagerRenderer(
                        this.getApplicationContext(),
                        map,
                        clusterManager
                );
                clusterManager.setRenderer(clusterManagerRenderer);
            }

            int placeNum = 0;
            clusterManager.clearItems();
            clusterMarkers.clear();
            clusterManager.cluster();
            for (Place place : places) {
                Log.d(TAG, "addMapMarkers: location: "
                        + place.getLatitude().toString()
                        + place.getLongitude().toString());
                try {
                    // асинхронная загрузка иконок
                    PictureLoader.loadPlaceThumbnail(this, place, placeNum,
                            clusterMarkers, clusterManager);

                    placeNum++;
                } catch (NullPointerException e) {
                    Log.e(TAG, "addMapMarkers: NullPointerException: " + e.getMessage());
                }
            }
        }
    }

    private void openShortPlaceInfo() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(
                MapsActivity.this, R.style.ButtonSheetDialogTheme
        );
        View bottomSheetView = LayoutInflater.from(getApplicationContext())
                .inflate(
                        R.layout.activity_map_bottom_sheet,
                        findViewById(R.id.bottomSheetContainer)
                );

        fillShortPlaceInfo(bottomSheetView, selectedPlace);

        bottomSheetView.findViewById(R.id.back_btn).setOnClickListener(view -> bottomSheetDialog.dismiss());

        bottomSheetView.findViewById(R.id.more_info_btn).setOnClickListener(view -> {
            bottomSheetDialog.dismiss();
            openFullPlaceInfo(view);
        });

        bottomSheetView.findViewById(R.id.visited_btn).setOnClickListener(this::addVisitedPlace);

        //Кнопка добавления места в избранные
        ImageButton liked = bottomSheetView.findViewById(R.id.favour_btn);
        liked.setOnClickListener(view -> {
            liked.setActivated(true);
            addFavoritePlace(view);
        });

        //Кнопка добавления места в посещенное
        ImageButton visited = bottomSheetView.findViewById(R.id.visited_btn);
        visited.setOnClickListener(view -> {
            visited.setActivated(true);
            addVisitedPlace(view);
        });

        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.show();
    }

    private void fillShortPlaceInfo(View view, ClusterMarker item) {
        TextView placeName = view.findViewById(R.id.place_name);
        placeName.setText(item.getTitle());
        ImageView placeImage = view.findViewById(R.id.place_image);
        placeImage.setImageBitmap(item.getIconPicture());

        ImageButton visited = view.findViewById(R.id.visited_btn);
        ImageButton favourite = view.findViewById(R.id.favour_btn);

        Context context = this;

        PlaceService placeService = ServiceGenerator.createService(PlaceService.class);
        Call<Place> placeCall = placeService.getPlace(item.getId());
        placeCall.enqueue(new Callback<Place>() {
            @Override
            public void onResponse(@NonNull Call<Place> call, @NonNull Response<Place> response) {
                TextView placeDescription = view.findViewById(R.id.place_description);
                if(response.isSuccessful() && response.body() != null){
                    String descriptionText = response.body().getDescription();
                    descriptionText = descriptionText.substring(0, 1).toUpperCase()
                            + descriptionText.substring(1);
                    placeDescription.setText(descriptionText);

                    selectedPlace.getPlace().setDescription(descriptionText);
                    selectedPlace.setId(item.getId());
                }
                else {
                    Toast.makeText(context, R.string.unexpectedErrorMsg,
                            Toast.LENGTH_SHORT).show();
                }

            }

            @Override
            public void onFailure(@NonNull Call<Place> call, @NonNull Throwable t) {

            }
        });

        //Проверка отображения значков избранного и посещенного
        Call<Boolean> visitedCheckCall = placeService.findVisitedById(item.getPlace().getId());
        visitedCheckCall.enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                if(response.isSuccessful() && response.body() != null){
                    if(response.body()){
                        visited.setImageResource(R.drawable.add_to_visited);
                    }
                    else {
                        visited.setImageResource(R.drawable.unknown);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {

            }
        });

        Call<Boolean> favouriteCheckCall = placeService.findFavouriteById(item.getPlace().getId());
        favouriteCheckCall.enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                if(response.isSuccessful() && response.body() != null){
                    if(response.body()){
                        favourite.setImageResource(R.drawable.add_to_favour);
                    }
                    else {
                        favourite.setImageResource(R.drawable.add_to_favour_not_active);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {

            }
        });

        RatingBar placeScore = view.findViewById(R.id.ratingBar_short_info);
        ScoreService scoreService = ServiceGenerator.createService(ScoreService.class);
        Call<Score> scoreCall = scoreService.getUserPlaceScore(
                AuthorizationHelper.getUserProfile().getId(),
                item.getId());
        scoreCall.enqueue(new Callback<Score>() {
            @Override
            public void onResponse(@NonNull Call<Score> call, @NonNull Response<Score> response) {
                if(response.code() == 200 && response.body() != null){
                    Long userMark = response.body().getScore();
                    placeScore.setRating((float)userMark);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Score> call, @NonNull Throwable t) {

            }
        });

        placeScore.setOnRatingBarChangeListener((ratingBar, v, b) -> {

            Call<Score> call = ServiceGenerator.createService(ScoreService.class)
                    .setScore(AuthorizationHelper.getUserProfile().getId(),
                            selectedPlace.getId(),
                            (int)v);

            call.enqueue(new Callback<Score>() {
                @Override
                public void onResponse(@NonNull Call<Score> call, @NonNull Response<Score> response) {
                    if(response.code() == 200 && response.body()!= null){
                        ratingBar.setRating(response.body().getScore());
                    }else if(response.code() == 400){

                    }
                    else {
                        Toast.makeText(context, R.string.unexpectedErrorMsg,
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Score> call, @NonNull Throwable t) {

                }
            });
        });
    }

    public void openFavourites(View view) {
        Intent intent = new Intent(this, FavouritesActivity.class);
        startActivity(intent);
    }

    private void openRecommenders(View view){
        locationUpdateThread.quit();
        finish();
        Intent intent = new Intent(this, ForYouActivity.class);
        startActivity(intent);
    }

    private void openUserPrefs(View view){
        Intent intent = new Intent(this, AccountActivity.class);
        startActivity(intent);
    }

    private void openFullPlaceInfo(View view){
        Intent intent = new Intent(this, PlaceActivity.class);

        intent.putExtra("id", selectedPlace.getId().toString());
        intent.putExtra("title", selectedPlace.getTitle());
        intent.putExtra("desc", selectedPlace.getPlace().getDescription());
        intent.putExtra("link", selectedPlace.getPlace().getThumbnailLink());

        startActivity(intent);
    }

    public void addFavoritePlace(View view) {
        Call<String> call = ServiceGenerator.createService(PlaceService.class)
                .addFavoritePlace(selectedPlace.getId());
        Context context = this;
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.code() == 200) {
                    ImageButton favour = view.findViewById(R.id.favour_btn);
                    favour.setImageResource(R.drawable.add_to_favour);
                    Toast.makeText(context, "Добавлено в избранное",
                            Toast.LENGTH_SHORT).show();
                } else if (response.code() == 400) {
                    //Вызываем метод удаления места
                    deleteFavouritePlace(selectedPlace.getId());
                    //При повторной поптыке нажатия на активную кнопку мы удаляем место из списка
                    //и делаем ее неактивной
                    ImageButton favour = view.findViewById(R.id.favour_btn);
                    favour.setImageResource(R.drawable.add_to_favour_not_active);
                } else {
                    Toast.makeText(context, R.string.unexpectedErrorMsg,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

            }
        });
    }

    private void addVisitedPlace(View view) {
        Call<String> call = ServiceGenerator.createService(PlaceService.class)
                .addVisitedPlace(selectedPlace.getId());
        Context context = this;
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.code() == 200) {
                    ImageButton visited = view.findViewById(R.id.visited_btn);
                    visited.setImageResource(R.drawable.add_to_visited);
                    Toast.makeText(context, "Добавлено в посещенное",
                            Toast.LENGTH_SHORT).show();
                } else if (response.code() == 400) {
                    //Вызываем метод удаления места
                    deleteVisitedPlace(selectedPlace.getId());
                    //При повторной поптыке нажатия на активную кнопку мы удаляем место из списка
                    //и делаем ее неактивной
                    ImageButton visited = view.findViewById(R.id.visited_btn);
                    visited.setImageResource(R.drawable.unknown);
                } else {
                    Toast.makeText(context, R.string.unexpectedErrorMsg,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

            }
        });
    }

    private void deleteVisitedPlace(Long placeId){
        Call<String> call = ServiceGenerator.createService(PlaceService.class)
                .deleteVisited(placeId);
        Context context = this;
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(response.code() == 200){
                    Toast.makeText(context, "Удалено из посещенного",
                            Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(context, R.string.unexpectedErrorMsg,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

            }
        });
    }

    private void deleteFavouritePlace(Long placeId){
        Call<String> call = ServiceGenerator.createService(PlaceService.class)
                .deleteFavourite(placeId);
        Context context = this;
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(response.code() == 200){
                    Toast.makeText(context, "Удалено из избранного",
                            Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(context, R.string.unexpectedErrorMsg,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

            }
        });
    }

    public void openVisited(View view) {
        Intent intent = new Intent(this, VisitedActivity.class);
        startActivity(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void openFilters(View view) {
        filtersLayout.removeAllViews();
        getCategories(() -> {
            List<Integer> categoryIds = new ArrayList<>();
            categories.forEach((id, categoryNameChosen) -> categoryIds.add(id));
            categoryIds.sort(Integer::compareTo);
            categoryIds.forEach(id -> {
                CategoryNameChosen categoryNameChosen = categories.get(id);
                assert categoryNameChosen != null;
                filtersLayout.addView(categoryView(id, categoryNameChosen.name, categoryNameChosen.chosen));
            });
        }, mapSlidingPane::close);
        mapSlidingPane.open();
    }

    private View categoryView(int id, String name, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(checked);
        checkBox.setText(name);
        checkBox.setTextSize(20);
        checkBox.setPadding(10, 10, 10, 10);
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> categories.put(id, new CategoryNameChosen(name, isChecked)));
        return checkBox;
    }

    private void getCategories(@Nullable Runnable onSuccess, @Nullable Runnable onFail) {
        BooleanWrapper firstFilling = new BooleanWrapper(false);
        if (categories == null) {
            categories = new HashMap<>();
            firstFilling.value = true;
        }
        CategoryService categoryService = ServiceGenerator.createService(CategoryService.class);
        categoryService.getCategories().enqueue(new Callback<List<Category>>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onResponse(@NonNull Call<List<Category>> call,
                                   @NonNull Response<List<Category>> response) {
                assert response.body() != null;
                response.body().forEach(category -> {
                    if (firstFilling.value) {
                        categories.put(category.getId(), new CategoryNameChosen(category.getName(), true));
                    } else {
                        categories.putIfAbsent(category.getId(), new CategoryNameChosen(category.getName(), true));
                    }
                });
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Category>> call, @NonNull Throwable t) {
                Log.w("Categories", "Could not load categories (" + t.getMessage() + ")");
                if (onFail != null) {
                    onFail.run();
                }
            }
        });
    }

    private static class BooleanWrapper {
        public boolean value;

        public BooleanWrapper(boolean value) {
            this.value = value;
        }
    }

    private static class CategoryNameChosen {
        public String name;
        public boolean chosen;

        public CategoryNameChosen(String name, boolean chosen) {
            this.name = name;
            this.chosen = chosen;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        locationUpdateThread.quit();
        if(showMapMode != ShowMapMode.ALL){
            Intent intent = new Intent(this, ForYouActivity.class);
            startActivity(intent);
        }
    }
}