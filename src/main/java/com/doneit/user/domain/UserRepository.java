package com.doneit.user.domain;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findActiveUser();
}
