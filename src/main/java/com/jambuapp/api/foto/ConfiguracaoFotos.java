package com.jambuapp.api.foto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(FotoProperties.class)
public class ConfiguracaoFotos {
    @Bean
    ArmazenamentoFotos armazenamentoFotos(FotoProperties config) {
        if ("supabase".equals(config.storageTipo())) {
            return new SupabaseStorageAdapter(config.supabaseUrl(), config.supabaseKey(),
                    config.supabaseBucket(), config.moderacaoTimeout());
        }
        return new ArmazenamentoFotosLocal(config.diretorioLocal());
    }

    @Bean
    ProvedorModeracaoImagem provedorModeracaoImagem(FotoProperties config, JsonMapper json) {
        return new RekognitionAdapter(config, json);
    }
}
