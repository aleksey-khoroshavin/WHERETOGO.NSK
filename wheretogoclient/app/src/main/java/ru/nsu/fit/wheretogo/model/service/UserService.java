package ru.nsu.fit.wheretogo.model.service;

import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import ru.nsu.fit.wheretogo.model.entity.User;

public interface UserService {
    @POST("/user/register")
    Call<User> registerUser(
            @Query("email") String email,
            @Query("username") String username,
            @Query("password") String password
    );

    @POST("/user/username")
    Call<User> setUsername(@Query("username") String username);

    @POST("/user/password")
    Call<Object> setPassword(@Query("password") String password);

    @POST("/user/logout")
    Call<Object> logout();

    @GET("/user")
    Call<User> getCurrentUser();

    @GET("/user/{id}")
    Call<User> getUser(@Path("id") Long id);
}