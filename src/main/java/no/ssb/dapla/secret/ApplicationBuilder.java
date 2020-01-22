package no.ssb.dapla.secret;

import no.ssb.helidon.application.DefaultHelidonApplicationBuilder;
import no.ssb.helidon.application.HelidonApplication;

import static java.util.Optional.ofNullable;

public class ApplicationBuilder extends DefaultHelidonApplicationBuilder {

    @Override
    public HelidonApplication build() {
        return new Application(ofNullable(this.config).orElseGet(DefaultHelidonApplicationBuilder::createDefaultConfig));
    }
}
