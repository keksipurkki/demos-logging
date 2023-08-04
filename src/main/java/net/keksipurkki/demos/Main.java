package net.keksipurkki.demos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        log.info("Starting {}", Main.class);
        System.exit(0);
    }
}
