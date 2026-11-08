package dev.hafnium.client.gui.render;

/**
 * Line icons drawn on a 24x24 grid (Lucide-style, 2-unit strokes). They are rasterised with anti-aliasing into a
 * texture at the exact on-screen pixel size, so they stay sharp at every GUI scale.
 */
public enum Icon {
    SWORDS(s -> s
            .poly(14.5f, 17.5f, 3, 6, 3, 3, 6, 3, 17.5f, 14.5f)
            .line(13, 19, 19, 13).line(16, 16, 20, 20).line(19, 21, 21, 19)
            .poly(14.5f, 6.5f, 18, 3, 21, 3, 21, 6, 17.5f, 9.5f)
            .line(5, 14, 9, 18).line(7, 17, 4, 20).line(3, 19, 5, 21)),
    RUNNER(s -> s
            .circle(14.5f, 4.2f, 2.1f)
            .line(13, 8.5f, 10.5f, 14)
            .poly(6, 11, 9, 8.5f, 13, 8.5f, 16, 11.5f, 19.5f, 11.5f)
            .poly(10.5f, 14, 14, 17, 13, 21.5f)
            .poly(10.5f, 14, 8.5f, 17.5f, 4.5f, 18.5f)),
    EYE(s -> s
            .arc(12, 15.94f, 10.75f, -158.5f, -21.5f)
            .arc(12, 8.06f, 10.75f, 21.5f, 158.5f)
            .circle(12, 12, 3)),
    USER(s -> s
            .circle(12, 7.5f, 4)
            .poly(19, 21, 19, 19, 17.8f, 16.2f, 15, 15, 9, 15, 6.2f, 16.2f, 5, 19, 5, 21)),
    GRID(s -> s
            .rect(3, 3, 7, 7, 1).rect(14, 3, 7, 7, 1).rect(14, 14, 7, 7, 1).rect(3, 14, 7, 7, 1)),
    GLOBE(s -> s
            .circle(12, 12, 9.5f)
            .line(2.5f, 12, 21.5f, 12)
            .arc(21.28f, 12, 13.28f, 134.3f, 225.7f)
            .arc(2.72f, 12, 13.28f, -45.7f, 45.7f)),
    BOT(s -> s
            .rect(4, 8, 16, 12, 2.5f)
            .line(12, 8, 12, 4.5f)
            .circle(12, 3.6f, 1.1f)
            .line(9, 13, 9, 14.5f).line(15, 13, 15, 14.5f)
            .line(2, 13, 2, 15).line(22, 13, 22, 15)),
    FOLDER(s -> s
            .poly(2, 5, 2, 18, 4, 20, 20, 20, 22, 18, 22, 8, 20, 6, 12.1f, 6, 10.4f, 5.1f, 9.6f, 3.9f, 7.9f, 3, 4, 3, 2, 5)),
    SEARCH(s -> s.circle(11, 11, 7.5f).line(21, 21, 16.4f, 16.4f)),
    GEAR(s -> s.gear(12, 12, 7.2f, 9.8f, 8).circle(12, 12, 3)),
    CLOSE(s -> s.line(18, 6, 6, 18).line(6, 6, 18, 18)),
    CHEVRON_DOWN(s -> s.poly(6, 9, 12, 15, 18, 9)),
    CHEVRON_LEFT(s -> s.poly(15, 18, 9, 12, 15, 6)),
    CHEVRON_RIGHT(s -> s.poly(9, 18, 15, 12, 9, 6)),
    PLUS(s -> s.line(12, 5, 12, 19).line(5, 12, 19, 12)),
    CHECK(s -> s.poly(20, 6, 9, 17, 4, 12)),
    TRASH(s -> s
            .line(3, 6, 21, 6)
            .poly(19, 6, 19, 20, 17, 22, 7, 22, 5, 20, 5, 6)
            .poly(8, 6, 8, 4, 10, 2, 14, 2, 16, 4, 16, 6)
            .line(10, 11, 10, 17).line(14, 11, 14, 17)),
    PENCIL(s -> s.poly(17, 3, 21, 7, 8, 20, 3, 21, 4, 16, 17, 3).line(15, 5, 19, 9)),
    SAVE(s -> s
            .poly(5, 3, 16, 3, 21, 8, 21, 19, 19, 21, 5, 21, 3, 19, 3, 5, 5, 3)
            .poly(17, 21, 17, 14, 7, 14, 7, 21)
            .poly(7, 3, 7, 8, 15, 8)),
    DOWNLOAD(s -> s
            .line(12, 3, 12, 15)
            .poly(7, 10, 12, 15, 17, 10)
            .poly(21, 15, 21, 19, 19, 21, 5, 21, 3, 19, 3, 15)),
    KEYBOARD(s -> s
            .rect(2, 5, 20, 14, 2)
            .line(6, 9, 6, 9).line(10, 9, 10, 9).line(14, 9, 14, 9).line(18, 9, 18, 9)
            .line(7, 15, 17, 15));

    final IconShape shape;

    Icon(java.util.function.UnaryOperator<IconShape> builder) {
        this.shape = builder.apply(new IconShape());
    }
}
