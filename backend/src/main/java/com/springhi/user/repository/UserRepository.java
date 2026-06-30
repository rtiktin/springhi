package com.springhi.user.repository;

import com.springhi.user.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.userType = 4 AND (u.email = :email OR (u.firstName IS NOT NULL AND u.firstName = :firstName AND u.lastName IS NOT NULL AND u.lastName = :lastName))")
    boolean existsSuspendedByEmailOrName(@Param("email") String email, @Param("firstName") String firstName, @Param("lastName") String lastName);

    List<User> findAllByOrderByCreatedAtDesc();
}
