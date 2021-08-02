package net.codebot.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.Paint.Style;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;

import java.util.ArrayList;

@SuppressLint("AppCompatCustomView")
public class PDFimage extends ImageView {

    final String LOGNAME = "pdf_image";

    // drawing path
    Path path = null;
    ArrayList<Path> paths = new ArrayList();
    /*
    ArrayList<String> paints = new ArrayList();
     */
    ArrayList<Paint> paints = new ArrayList();
    ArrayList<String> operations = new ArrayList();

    boolean isErasing = false;
    boolean isErasingDone = false;

    Region clip = new Region(0, 0, 5000, 5000);

    // image to display
    Bitmap bitmap;
    Paint paint, pencil, highlighter;

    // constructor
    public PDFimage(Context context) {
        super(context);
        createPaintPencil();
        createPaintHighlighter();
        setBrush(pencil);
    }

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d(LOGNAME, "Action down");
                path = new Path();
                path.moveTo(event.getX(), event.getY());
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d(LOGNAME, "Action move");
                path.lineTo(event.getX(), event.getY());
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                Log.d(LOGNAME, "Action up");
                if (!isErasing) {
                    paths.add(path);
                    /*
                    if (paint == pencil) {
                        paints.add("pencil");
                    } else {
                        paints.add("highlighter");
                    }
                    */
                    paints.add(paint);
                    if (paint == pencil) {
                        operations.add("draw");
                    } else if (paint == highlighter) {
                        operations.add("highlight");
                    }
                } else {
                    paths.add(path);
                    paints.add(null);
                    operations.add("erase");
                    isErasingDone = true;
                }

                invalidate();
                break;
        }
        return true;
    }

    // set image as background
    public void setImage(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    // set brush characteristics, e.g. color, thickness, alpha
    public void setBrush(Paint paint) {
        this.path = null;
        this.paint = paint;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // draw background
        if (bitmap != null) {
            this.setImageBitmap(bitmap);
        }

        // erase existing drawing or highlighting
        if (isErasing && isErasingDone) {
            eraseDrawings();
        }

        // draw lines over it
        for (int i = 0; i < paths.size(); i++) {
            Path p = paths.get(i);
            /*
            String s = paints.get(i);
            if (s.equals("pencil")) {
                canvas.drawPath(p, pencil);
            } else {
                canvas.drawPath(p, highlighter);
            }
             */
            Paint pPaint = paints.get(i);
            if (pPaint != null) { // do not draw when it is an erasing path
                canvas.drawPath(p, pPaint);
            }
        }

        if (path != null && !isErasing) {
            canvas.drawPath(path, paint);
        }
    }

    private void createPaintPencil() {
        pencil = new Paint();
        pencil.setStyle(Style.STROKE);
        pencil.setColor(Color.BLUE);
        pencil.setStrokeWidth(5);
    }

    private void createPaintHighlighter() {
        highlighter = new Paint();
        highlighter.setStyle(Style.STROKE);
        highlighter.setColor(Color.YELLOW);
        highlighter.setStrokeWidth(30);
        highlighter.setAlpha(50);
        highlighter.setAntiAlias(true);
        highlighter.setStrokeCap(Paint.Cap.ROUND);
        highlighter.setStrokeJoin(Paint.Join.ROUND);
    }

    public Paint getPencil() {
        return pencil;
    }

    public Paint getHighlighter() {
        return highlighter;
    }

    private void eraseDrawings() {
        Region erasePathRegion = new Region();
        erasePathRegion.setPath(path, clip);
        Region existingPathRegion = new Region();
        for (int i = 0; i < paths.size() - 1; i++) { // the erase path is the last item in paths
            Paint existingPaint = paints.get(i);
            if (existingPaint == null) {
                continue;
            }
            Path existingPath = paths.get(i);
            existingPathRegion.setPath(existingPath, clip);
            if (existingPathRegion.op(erasePathRegion, Region.Op.INTERSECT)) {
                paints.set(i, null);
            }
        }
        isErasingDone = false;
    }
}
