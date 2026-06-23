package com.wa.banking.accounts.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wa.banking.accounts.entity.AccountEntity;
import com.wa.banking.accounts.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Carga datos demo de cuentas en MongoDB al iniciar en perfil local.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class AccountSeedDataLoader implements ApplicationRunner {

    private static final String CLASSPATH_SEED = "data/accounts-seed.json";

    private static final String FILESYSTEM_SEED = "data-local/accounts-seed.json";

    private final AccountRepository accountRepository;

    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (accountRepository.count() > 0) {
            log.info("Accounts collection already populated, skipping seed");
            return;
        }

        List<AccountEntity> seedAccounts = loadSeedAccounts();
        accountRepository.saveAll(seedAccounts);
        log.info("Seeded {} demo account(s) for local profile", seedAccounts.size());
    }

    private List<AccountEntity> loadSeedAccounts() throws IOException {
        InputStream inputStream = openSeedStream();
        try (inputStream) {
            return objectMapper.readValue(inputStream, new TypeReference<List<AccountEntity>>() {
            });
        }
    }

    private InputStream openSeedStream() throws IOException {
        Path filesystemSeed = Path.of(FILESYSTEM_SEED);
        if (Files.exists(filesystemSeed)) {
            log.info("Loading account seed from {}", filesystemSeed.toAbsolutePath());
            return Files.newInputStream(filesystemSeed);
        }

        ClassPathResource classpathResource = new ClassPathResource(CLASSPATH_SEED);
        if (classpathResource.exists()) {
            log.info("Loading account seed from classpath:{}", CLASSPATH_SEED);
            return classpathResource.getInputStream();
        }

        throw new IOException("Account seed file not found at " + FILESYSTEM_SEED + " or classpath:" + CLASSPATH_SEED);
    }
}
