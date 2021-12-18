package ru.nsu.fit.wheretogo.entity;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

import lombok.experimental.Accessors;
import ru.nsu.fit.wheretogo.dto.UserDto;

@Entity
@Table(name = "users")
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",nullable = false)
    private Long id;

    @Column(name = "email",nullable = false)
    private String email;

    @Column(name = "password",nullable = false)
    private String password;

    @Column(name = "username")
    private String username;

    @Column(name = "created_at")
    private Instant createdAt;


    @ManyToMany
    @JoinTable(
            name = "user_visited_places",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "place_id")
    )
    private List<Place> visitedPlaces;

    @ManyToMany
    @JoinTable(
            name = "user_favourites",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "place_id")
    )
    private List<Place> favouritePlaces;

    public User(String email, String password, String username, Instant createdAt) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.createdAt = createdAt;;
    }

    public static User getFromDTO(UserDto dto) {
        if (dto == null) {
            return null;
        }
        return new User()
                .setId(dto.getId())
                .setEmail(dto.getEmail())
                .setUsername(dto.getUsername())
                .setCreatedAt(dto.getCreatedAt());
    }
}
