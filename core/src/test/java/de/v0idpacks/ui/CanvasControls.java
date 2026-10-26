package de.v0idpacks.ui;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise actual rendered hit targets without binding behavior tests to layout pixels. */
final class CanvasControls {
    private CanvasControls() {}
    static void clickControl(EditorCanvas canvas,String id) {
        canvas.draw();
        var box=canvas.controlBounds(id);
        assertTrue(box.x>=0&&box.y>=0&&box.x+box.width<=canvas.width()&&box.y+box.height<=canvas.height(),id+" must fit the canvas");
        assertTrue(canvas.click(box.x+box.width/2,box.y+box.height/2,0));
    }
}
