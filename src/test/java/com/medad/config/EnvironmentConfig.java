package com.medad.config;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvironmentConfig {

    public static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private EnvironmentConfig() {}



}
