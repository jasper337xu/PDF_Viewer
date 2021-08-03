package net.codebot.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.Paint.Style;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

@SuppressLint("AppCompatCustomView")
public class PDFimage extends ImageView {

    final String LOGNAME = "pdf_image";

    int pageIndex = 0;

    // drawing path
    Path path = null;
    Map<Integer, ArrayList<Path>> idxToPaths = new HashMap<>();
    Map<Integer, ArrayList<Paint>> idxToPaints = new HashMap<>();
    Map<Integer, ArrayList<String>> idxToOperations = new HashMap<>();

    Map<Integer, Stack<Path>> idxToPathsStack1 = new HashMap<>();
    Map<Integer, Stack<Paint>> idxToPaintsStack1 = new HashMap<>();
    Map<Integer, Stack<String>> idxToOperationsStack1 = new HashMap<>();
    Map<Integer, Stack<Path>> idxToPathsStack2 = new HashMap<>();
    Map<Integer, Stack<Paint>> idxToPaintsStack2 = new HashMap<>();
    Map<Integer, Stack<String>> idxToOperationsStack2 = new HashMap<>();

    boolean isErasing = false;
    boolean isErasingDone = false;

    Region clip = new Region(0, 0, 5000, 5000);

    // image to display
    Bitmap bitmap;
    Paint paint, pencil, highlighter;
    PdfRenderer.Page curPage;

    // constructor
    public PDFimage(Context context) {
        super(context);
        createPaintPencil();
        createPaintHighlighter();
        setBrush(pencil);
    }

    // we save a lot of points because they need to be processed
    // during touch events e.g. ACTION_MOVE
    float x1, x2, y1, y2, old_x1, old_y1, old_x2, old_y2;
    float mid_x = -1f, mid_y = -1f, old_mid_x = -1f, old_mid_y = -1f;
    int p1_id, p1_index, p2_id, p2_index;

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    Matrix matrix = new Matrix();
    Matrix inverse = new Matrix();
    boolean isMatrixZoom = false;

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 1) { // 1 point is drawing or erasing
            p1_id = event.getPointerId(0);
            p1_index = event.findPointerIndex(p1_id);

            // invert using the current matrix to account for pan/scale
            // inverts in-place and returns boolean
            inverse = new Matrix();
            matrix.invert(inverse);

            // mapPoints returns values in-place
            float[] inverted = new float[] { event.getX(p1_index), event.getY(p1_index) };
            inverse.mapPoints(inverted);
            x1 = inverted[0];
            y1 = inverted[1];

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.d(LOGNAME, "Action down");
                    path = new Path();
                    path.moveTo(x1, y1);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.d(LOGNAME, "Action move");
                    path.lineTo(x1, y1);
                    break;
                case MotionEvent.ACTION_UP:
                    Log.d(LOGNAME, "Action up");
                    handleActionUp();
                    break;
            }
        }
        else if (event.getPointerCount() == 2) { // 2 points is zoom/pan
            // point 1
            p1_id = event.getPointerId(0);
            p1_index = event.findPointerIndex(p1_id);
            // mapPoints returns values in-place
            float[] inverted = new float[] { event.getX(p1_index), event.getY(p1_index) };
            inverse.mapPoints(inverted);
            // first pass, initialize the old == current value
            if (old_x1 < 0 || old_y1 < 0) {
                old_x1 = x1 = inverted[0];
                old_y1 = y1 = inverted[1];
            } else {
                old_x1 = x1;
                old_y1 = y1;
                x1 = inverted[0];
                y1 = inverted[1];
            }

            // point 2
            p2_id = event.getPointerId(1);
            p2_index = event.findPointerIndex(p2_id);
            // mapPoints returns values in-place
            inverted = new float[] { event.getX(p2_index), event.getY(p2_index) };
            inverse.mapPoints(inverted);
            // first pass, initialize the old == current value
            if (old_x2 < 0 || old_y2 < 0) {
                old_x2 = x2 = inverted[0];
                old_y2 = y2 = inverted[1];
            } else {
                old_x2 = x2;
                old_y2 = y2;
                x2 = inverted[0];
                y2 = inverted[1];
            }

            // midpoint
            mid_x = (x1 + x2) / 2;
            mid_y = (y1 + y2) / 2;
            old_mid_x = (old_x1 + old_x2) / 2;
            old_mid_y = (old_y1 + old_y2) / 2;

            // distance
            float d_old = (float) Math.sqrt(Math.pow((old_x1 - old_x2), 2) + Math.pow((old_y1 - old_y2), 2));
            float d = (float) Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));

            // pan and zoom during MOVE event
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                Log.d(LOGNAME, "Multitouch move");
                // pan == translate of midpoint
                float dx = mid_x - old_mid_x;
                float dy = mid_y - old_mid_y;
                matrix.preTranslate(dx, dy);
                isMatrixZoom = true;
                Log.d(LOGNAME, "translate: " + dx + "," + dy);

                // zoom == change of spread between p1 and p2
                float scale = d/d_old;
                scale = Math.max(0, scale);
                matrix.preScale(scale, scale, mid_x, mid_y);
                Log.d(LOGNAME, "scale: " + scale);

            // reset on up
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                old_x1 = -1f;
                old_y1 = -1f;
                old_x2 = -1f;
                old_y2 = -1f;
                old_mid_x = -1f;
                old_mid_y = -1f;
            }
        }
        return true;
    }

    private void handleActionUp() {
        if (!isErasing) {
            if (!idxToPaths.containsKey(pageIndex)) {
                idxToPaths.put(pageIndex, new ArrayList<Path>());
            }
            idxToPaths.get(pageIndex).add(path);

            if (!idxToPaints.containsKey(pageIndex)) {
                idxToPaints.put(pageIndex, new ArrayList<Paint>());
            }
            idxToPaints.get(pageIndex).add(paint);

            if (paint == pencil) {
                if (!idxToOperations.containsKey(pageIndex)) {
                    idxToOperations.put(pageIndex, new ArrayList<String>());
                }
                idxToOperations.get(pageIndex).add("draw");
            } else if (paint == highlighter) {
                if (!idxToOperations.containsKey(pageIndex)) {
                    idxToOperations.put(pageIndex, new ArrayList<String>());
                }
                idxToOperations.get(pageIndex).add("highlight");
            }

            updateStacks1ForDraw();
        } else {
            if (!idxToPaths.containsKey(pageIndex)) {
                idxToPaths.put(pageIndex, new ArrayList<Path>());
            }
            idxToPaths.get(pageIndex).add(path);

            if (!idxToPaints.containsKey(pageIndex)) {
                idxToPaints.put(pageIndex, new ArrayList<Paint>());
            }
            idxToPaints.get(pageIndex).add(null);

            if (!idxToOperations.containsKey(pageIndex)) {
                idxToOperations.put(pageIndex, new ArrayList<String>());
            }
            idxToOperations.get(pageIndex).add("erase");

            updateStacks1ForErase();
            isErasingDone = true;
        }

        invalidate();
    }

    private void updateStacks1ForDraw() {
        // push to paths stack1 of this page
        if (!idxToPathsStack1.containsKey(pageIndex)) {
            idxToPathsStack1.put(pageIndex, new Stack<Path>());
        }
        idxToPathsStack1.get(pageIndex).push(path);
        // push to paints stack1 of this page
        if (!idxToPaintsStack1.containsKey(pageIndex)) {
            idxToPaintsStack1.put(pageIndex, new Stack<Paint>());
        }
        idxToPaintsStack1.get(pageIndex).push(paint);
        // push to operations stack1 of this page
        if (paint == pencil) {
            if (!idxToOperationsStack1.containsKey(pageIndex)) {
                idxToOperationsStack1.put(pageIndex, new Stack<String>());
            }
            idxToOperationsStack1.get(pageIndex).push("draw");
        } else if (paint == highlighter) {
            if (!idxToOperationsStack1.containsKey(pageIndex)) {
                idxToOperationsStack1.put(pageIndex, new Stack<String>());
            }
            idxToOperationsStack1.get(pageIndex).push("highlight");
        }
    }

    private void updateStacks1ForErase() {
        // push to paths stack1 of this page
        if (!idxToPathsStack1.containsKey(pageIndex)) {
            idxToPathsStack1.put(pageIndex, new Stack<Path>());
        }
        idxToPathsStack1.get(pageIndex).push(path);
        // push to paints stack1 of this page
        if (!idxToPaintsStack1.containsKey(pageIndex)) {
            idxToPaintsStack1.put(pageIndex, new Stack<Paint>());
        }
        idxToPaintsStack1.get(pageIndex).push(null);
        // push to operations stack1 of this page
        if (!idxToOperationsStack1.containsKey(pageIndex)) {
            idxToOperationsStack1.put(pageIndex, new Stack<String>());
        }
        idxToOperationsStack1.get(pageIndex).push("erase");
    }

    public void handleUndo() {
        if (!idxToPathsStack1.containsKey(pageIndex)) {
            return;
        }
        Stack<Path> curPathsStack1 = idxToPathsStack1.get(pageIndex);
        Stack<Paint> curPaintsStack1 = idxToPaintsStack1.get(pageIndex);
        Stack<String> curOperationsStack1 = idxToOperationsStack1.get(pageIndex);
        if (curPathsStack1.isEmpty()) {
            return;
        }

        // pop path, paint, operation from stack1
        Path p = curPathsStack1.pop();
        Paint pPaint = curPaintsStack1.pop();
        String op = curOperationsStack1.pop();
        // push path, paint, operation to stack2
        if (!idxToPathsStack2.containsKey(pageIndex)) {
            idxToPathsStack2.put(pageIndex, new Stack<Path>());
            idxToPaintsStack2.put(pageIndex, new Stack<Paint>());
            idxToOperationsStack2.put(pageIndex, new Stack<String>());
        }
        idxToPathsStack2.get(pageIndex).push(p);
        idxToPaintsStack2.get(pageIndex).push(pPaint);
        idxToOperationsStack2.get(pageIndex).push(op);

        // remove the last path, paint, operation from ArrayList
        int lastIndex = idxToPaths.get(pageIndex).size() - 1;
        idxToPaths.get(pageIndex).remove(lastIndex);
        idxToPaints.get(pageIndex).remove(lastIndex);
        idxToOperations.get(pageIndex).remove(lastIndex);

        if (op.equals("erase")) {
            revertPaintForIntersectingPaths(p, idxToPaths, idxToPaints, idxToOperations);
        }
    }

    public void handleRedo() {
        if (!idxToPathsStack2.containsKey(pageIndex)) {
            return;
        }
        Stack<Path> curPathsStack2 = idxToPathsStack2.get(pageIndex);
        Stack<Paint> curPaintsStack2 = idxToPaintsStack2.get(pageIndex);
        Stack<String> curOperationsStack2 = idxToOperationsStack2.get(pageIndex);
        if (curPathsStack2.isEmpty()) {
            return;
        }

        // pop path, paint, operation from stack2
        Path p = curPathsStack2.pop();
        Paint pPaint = curPaintsStack2.pop();
        String op = curOperationsStack2.pop();
        // push path, paint, operation to stack1
        if (!idxToPathsStack1.containsKey(pageIndex)) {
            idxToPathsStack1.put(pageIndex, new Stack<Path>());
            idxToPaintsStack1.put(pageIndex, new Stack<Paint>());
            idxToOperationsStack1.put(pageIndex, new Stack<String>());
        }
        idxToPathsStack1.get(pageIndex).push(p);
        idxToPaintsStack1.get(pageIndex).push(pPaint);
        idxToOperationsStack1.get(pageIndex).push(op);

        // add the last path, paint, operation to ArrayList
        idxToPaths.get(pageIndex).add(p);
        idxToPaints.get(pageIndex).add(pPaint);
        idxToOperations.get(pageIndex).add(op);

        if (op.equals("erase")) {
            modifyPaintForIntersectingPaths(p, idxToPaths, idxToPaints, idxToOperations);
        }
    }

    // for undoing erase
    private void revertPaintForIntersectingPaths(Path erasePath,
                                                 Map<Integer, ArrayList<Path>> pathsMap,
                                                 Map<Integer, ArrayList<Paint>> paintsMap,
                                                 Map<Integer, ArrayList<String>> operationsMap) {
        Region erasePathRegion = new Region();
        erasePathRegion.setPath(erasePath, clip);
        Region existingPathRegion = new Region();

        ArrayList<Path> curPaths = new ArrayList<>();
        ArrayList<Paint> curPaints = new ArrayList<>();
        if (pathsMap.containsKey(pageIndex) && paintsMap.containsKey(pageIndex)) {
            curPaths = pathsMap.get(pageIndex);
            curPaints = paintsMap.get(pageIndex);
        }

        for (int i = 0; i < curPaths.size(); i++) { // no need to - 1 since we've removed the last erase path
            Paint existingPaint = curPaints.get(i);
            String operation = operationsMap.get(pageIndex).get(i);
            if (existingPaint == null && operation.equals("erase")) {
                continue;
            }
            Path existingPath = curPaths.get(i);
            existingPathRegion.setPath(existingPath, clip);
            if (existingPathRegion.op(erasePathRegion, Region.Op.INTERSECT)) {
                if (operation.equals("draw")) {
                    curPaints.set(i, pencil);
                } else if (operation.equals("highlight")) {
                    curPaints.set(i, highlighter);
                } else { // should not reach here
                    curPaints.set(i, null);
                }
            }
        }
    }

    // for redoing erase
    private void modifyPaintForIntersectingPaths(Path erasePath,
                                                 Map<Integer, ArrayList<Path>> pathsMap,
                                                 Map<Integer, ArrayList<Paint>> paintsMap,
                                                 Map<Integer, ArrayList<String>> operationsMap) {
        Region erasePathRegion = new Region();
        erasePathRegion.setPath(erasePath, clip);
        Region existingPathRegion = new Region();

        ArrayList<Path> curPaths = new ArrayList<>();
        ArrayList<Paint> curPaints = new ArrayList<>();
        if (pathsMap.containsKey(pageIndex) && paintsMap.containsKey(pageIndex)) {
            curPaths = pathsMap.get(pageIndex);
            curPaints = paintsMap.get(pageIndex);
        }

        for (int i = 0; i < curPaths.size() - 1; i++) {
            Paint existingPaint = curPaints.get(i);
            String operation = operationsMap.get(pageIndex).get(i);
            if (operation.equals("erase") || existingPaint == null) {
                continue;
            }
            Path existingPath = curPaths.get(i);
            existingPathRegion.setPath(existingPath, clip);
            if (existingPathRegion.op(erasePathRegion, Region.Op.INTERSECT)) {
                curPaints.set(i, null);
            }
        }
    }

    public void setCurPage(PdfRenderer.Page curPage) {
        this.curPage = curPage;
    }

    void setIsMatrixZoom(boolean isMatrixZoom) {
        this.isMatrixZoom = isMatrixZoom;
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // apply transformations from the event handler above
        canvas.concat(matrix);

        // draw background
        if (bitmap != null) {
            if (isMatrixZoom) {
                bitmap = Bitmap.createBitmap(curPage.getWidth(), curPage.getHeight(), Bitmap.Config.ARGB_8888);
                curPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } else {
                bitmap = Bitmap.createBitmap(curPage.getWidth(), curPage.getHeight(), Bitmap.Config.ARGB_8888);
                curPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
            this.setImageBitmap(bitmap);
        }

        // erase existing drawing or highlighting
        if (isErasing && isErasingDone) {
            eraseDrawings();
        }

        // draw lines over it
        ArrayList<Path> curPaths = new ArrayList<>();
        ArrayList<Paint> curPaints = new ArrayList<>();
        if (idxToPaths.containsKey(pageIndex) && idxToPaints.containsKey(pageIndex)) {
            curPaths = idxToPaths.get(pageIndex);
            curPaints = idxToPaints.get(pageIndex);
        }
        for (int i = 0; i < curPaths.size(); i++) {
            Path p = curPaths.get(i);
            Paint pPaint = curPaints.get(i);
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

        ArrayList<Path> curPaths = new ArrayList<>();
        ArrayList<Paint> curPaints = new ArrayList<>();
        if (idxToPaths.containsKey(pageIndex) && idxToPaints.containsKey(pageIndex)) {
            curPaths = idxToPaths.get(pageIndex);
            curPaints = idxToPaints.get(pageIndex);
        }
        for (int i = 0; i < curPaths.size() - 1; i++) { // the erase path is the last item in paths
            Paint existingPaint = curPaints.get(i);
            if (existingPaint == null) {
                continue;
            }

            Path existingPath = curPaths.get(i);
            existingPathRegion.setPath(existingPath, clip);
            if (existingPathRegion.op(erasePathRegion, Region.Op.INTERSECT)) {
                curPaints.set(i, null);
            }
        }
        isErasingDone = false;
    }

    void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    void setPathToNull() {
        this.path = null;
    }
}
