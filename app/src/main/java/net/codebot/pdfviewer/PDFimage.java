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
                paths.add(path);
                /*
                if (paint == pencil) {
                    paints.add("pencil");
                } else {
                    paints.add("highlighter");
                }
                 */
                paints.add(paint);
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
            canvas.drawPath(p, pPaint);
        }

        if (path != null) {
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
}
