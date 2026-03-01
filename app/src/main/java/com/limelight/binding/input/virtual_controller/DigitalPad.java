/**
 * Created by Karim Mreisi.
 */
package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

public class DigitalPad extends VirtualControllerElement {
        public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
        int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
        public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
        public final static int DIGITAL_PAD_DIRECTION_UP = 2;
        public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
        public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
        List<DigitalPadListener> listeners = new ArrayList<>();
        private static final int DPAD_MARGIN = 5;
        private final Paint paint = new Paint();

        public DigitalPad(VirtualController controller, Context context) {
                super(controller, context, EID_DPAD);
        }

        public void addDigitalPadListener(DigitalPadListener listener) {
                listeners.add(listener);
        }

        @Override
        protected void onElementDraw(Canvas canvas) {
                // set transparent background
                canvas.drawColor(Color.TRANSPARENT);
                paint.setTextSize(getPercent(getCorrectWidth(), 20));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStrokeWidth(getDefaultStrokeWidth());
                paint.setStyle(Paint.Style.STROKE);
                paint.setAntiAlias(true);

                float w = getWidth();
                float h = getHeight();
                float strokeW = paint.getStrokeWidth();

                // Cross dimensions: arm width is ~33% of the element size
                float armWidth = Math.min(w, h) * 0.33f;
                float cornerRadius = armWidth * 0.3f;

                // Center of cross
                float cx = w / 2f;
                float cy = h / 2f;

                // Horizontal bar bounds (left arm + right arm)
                float hLeft = strokeW + DPAD_MARGIN;
                float hTop = cy - armWidth / 2f;
                float hRight = w - strokeW - DPAD_MARGIN;
                float hBottom = cy + armWidth / 2f;

                // Vertical bar bounds (up arm + down arm)
                float vLeft = cx - armWidth / 2f;
                float vTop = strokeW + DPAD_MARGIN;
                float vRight = cx + armWidth / 2f;
                float vBottom = h - strokeW - DPAD_MARGIN;

                // Determine pressed state for each direction
                boolean leftPressed = (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0;
                boolean rightPressed = (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0;
                boolean upPressed = (direction & DIGITAL_PAD_DIRECTION_UP) > 0;
                boolean downPressed = (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0;

                // Build a cross-shaped path with rounded corners at each arm tip
                Path crossPath = new Path();
                // Start from top-left of the up arm
                crossPath.moveTo(vLeft, vTop + cornerRadius);
                // Top-left corner of up arm
                crossPath.arcTo(new RectF(vLeft, vTop, vLeft + cornerRadius * 2, vTop + cornerRadius * 2), 180, 90);
                // Top-right corner of up arm
                crossPath.arcTo(new RectF(vRight - cornerRadius * 2, vTop, vRight, vTop + cornerRadius * 2), 270, 90);
                // Down to where right arm starts
                crossPath.lineTo(vRight, hTop);
                // Right arm top-left corner -> top-right corner
                crossPath.lineTo(hRight - cornerRadius, hTop);
                crossPath.arcTo(new RectF(hRight - cornerRadius * 2, hTop, hRight, hTop + cornerRadius * 2), 270, 90);
                // Right arm bottom-right corner
                crossPath.arcTo(new RectF(hRight - cornerRadius * 2, hBottom - cornerRadius * 2, hRight, hBottom), 0, 90);
                // Back to where down arm starts
                crossPath.lineTo(vRight, hBottom);
                // Down arm top-right -> bottom-right corner
                crossPath.lineTo(vRight, vBottom - cornerRadius);
                crossPath.arcTo(new RectF(vRight - cornerRadius * 2, vBottom - cornerRadius * 2, vRight, vBottom), 0, 90);
                // Down arm bottom-left corner
                crossPath.arcTo(new RectF(vLeft, vBottom - cornerRadius * 2, vLeft + cornerRadius * 2, vBottom), 90, 90);
                // Up to where left arm starts
                crossPath.lineTo(vLeft, hBottom);
                // Left arm bottom-right -> bottom-left corner
                crossPath.lineTo(hLeft + cornerRadius, hBottom);
                crossPath.arcTo(new RectF(hLeft, hBottom - cornerRadius * 2, hLeft + cornerRadius * 2, hBottom), 90, 90);
                // Left arm top-left corner
                crossPath.arcTo(new RectF(hLeft, hTop, hLeft + cornerRadius * 2, hTop + cornerRadius * 2), 180, 90);
                // Back to start
                crossPath.lineTo(vLeft, hTop);
                crossPath.lineTo(vLeft, vTop + cornerRadius);
                crossPath.close();

                // Draw the full cross outline in default color
                paint.setColor(getDefaultColor());
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(crossPath, paint);

                // Draw highlighted arm outlines when pressed
                paint.setColor(pressedColor);
                paint.setStyle(Paint.Style.STROKE);
                if (leftPressed) {
                        Path leftArm = new Path();
                        leftArm.addRoundRect(new RectF(hLeft, hTop, cx, hBottom), cornerRadius, cornerRadius, Path.Direction.CW);
                        leftArm.op(crossPath, Path.Op.INTERSECT);
                        canvas.drawPath(leftArm, paint);
                }
                if (rightPressed) {
                        Path rightArm = new Path();
                        rightArm.addRoundRect(new RectF(cx, hTop, hRight, hBottom), cornerRadius, cornerRadius, Path.Direction.CW);
                        rightArm.op(crossPath, Path.Op.INTERSECT);
                        canvas.drawPath(rightArm, paint);
                }
                if (upPressed) {
                        Path upArm = new Path();
                        upArm.addRoundRect(new RectF(vLeft, vTop, vRight, cy), cornerRadius, cornerRadius, Path.Direction.CW);
                        upArm.op(crossPath, Path.Op.INTERSECT);
                        canvas.drawPath(upArm, paint);
                }
                if (downPressed) {
                        Path downArm = new Path();
                        downArm.addRoundRect(new RectF(vLeft, cy, vRight, vBottom), cornerRadius, cornerRadius, Path.Direction.CW);
                        downArm.op(crossPath, Path.Op.INTERSECT);
                        canvas.drawPath(downArm, paint);
                }
        }

        private void newDirectionCallback(int direction) {
                _DBG("direction: " + direction);
                // notify listeners
                for (DigitalPadListener listener : listeners) {
                        listener.onDirectionChange(direction);
                }
        }

        @Override
        public boolean onElementTouchEvent(MotionEvent event) {
                // get masked (not specific to a pointer) action
                switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE: {
                                direction = 0;
                                if (event.getX() < getPercent(getWidth(), 33)) {
                                        direction |= DIGITAL_PAD_DIRECTION_LEFT;
                                }
                                if (event.getX() > getPercent(getWidth(), 66)) {
                                        direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                                }
                                if (event.getY() > getPercent(getHeight(), 66)) {
                                        direction |= DIGITAL_PAD_DIRECTION_DOWN;
                                }
                                if (event.getY() < getPercent(getHeight(), 33)) {
                                        direction |= DIGITAL_PAD_DIRECTION_UP;
                                }
                                newDirectionCallback(direction);
                                invalidate();
                                return true;
                        }
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP: {
                                direction = 0;
                                newDirectionCallback(direction);
                                invalidate();
                                return true;
                        }
                        default: {
                        }
                }
                return true;
        }

        public interface DigitalPadListener {
                void onDirectionChange(int direction);
        }
}
