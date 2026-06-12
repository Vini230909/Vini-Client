package vini.client;

public interface ViniFeature {
    void init();

    default void loadContent() {
    }
}
