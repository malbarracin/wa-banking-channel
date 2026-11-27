package com.bcra.channel.ws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.bcra.channel.ws.config.BankChannelProperties;
import com.bcra.channel.ws.config.ChannelMessages;

@SpringBootTest
class WaChannelServiceApplicationTest {

    @Autowired
    private BankChannelProperties bankChannelProperties;

    @Autowired
    private ChannelMessages channelMessages;

    @Test
    @DisplayName("El contexto de Spring arranca con H2 y los beans base")
    void contextLoads() {
        assertThat(bankChannelProperties).isNotNull();
        assertThat(channelMessages).isNotNull();
    }
}
