package com.bank.users.user.entity;

/** Estado operativo; únicamente ACTIVE permite nuevos vínculos de canal. */
public enum UserStatus {
    ACTIVE, SUSPENDED, LOGICALLY_DELETED
}
