/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a analog stick on screen element. It is used to get 2-Axis user input.
 */
public class AnalogStickFree extends VirtualControllerElement {

    /**
     * outer radius size in percent of the ui element
     */
    public static final int SIZE_RADIUS_COMPLETE = 90;
    /**
     * analog stick size in percent of the ui element
     */
    public static final int SIZE_RADIUS_ANALOG_STICK = 90;
    /**
     * dead zone size in percent of the ui element
     */
    public static final int SIZE_RADIUS_DEADZONE = 90;
    /**
     * time frame for a double click
     */
    public final static long timeoutDoubleClick = 350;

    /**
     * touch down time until the deadzone is lifted to allow precise movements with the analog sticks
     */
    public final static long timeoutDeadzone = 150;

    /**
     * Listener interface to update registered observers.
     */
    public interface AnalogStickListener {

        /**
         * onMovement event will be fired on real analog stick movement (outside of the deadzone).
         *
         * @param x horizontal position, value from -1.0 ... 0 .. 1.0
         * @param y vertical position, value from -1.0 ... 0 .. 1.0
         */
        void onMovement(float x, float y);

        /**
         * onClick event will be fired on click on the analog stick
         */
        void onClick();

        /**
         * onDoubleClick event will be fired on a double click in a short time frame on the analog
         * stick.
         */
        void onDoubleClick();

        /**
         * onRevoke event will be fired on unpress of the analog stick.
         */
        void onRevoke();
    }

    /**
     * Movement states of the analog sick.
     */
    private enum STICK_STATE {
        NO_MOVEMENT,
        MOVED_IN_DEAD_ZONE,
        MOVED_ACTIVE
    }

    /**
     * Click type states.
     */
    private enum CLICK_STATE {
        SINGLE,
        DOUBLE
    }

    /**
     * configuration if the analog stick should be displayed as circle or square
     */
    private boolean circle_stick = true; // TODO: implement square sick for simulations

    /**
     * outer radius, this size will be automatically updated on resize
     */
    private float radius_complete = 0;
    /**
     * analog stick radius, this size will be automatically updated on resize
     */
    private float radius_analog_stick = 0;
    /**
     * dead zone radius, this size will be automatically updated on resize
     */
    private float radius_dead_zone = 0;

    /**
     * horizontal position in relation to the center of the element
     */
    private float relative_x = 0;
    /**
     * vertical position in relation to the center of the element
     */
    private float relative_y = 0;

    private boolean bIsFingerOnScreen = false;

    private double movement_radius = 0;
    private double movement_angle = 0;

    private float position_stick_x = 0;
    private float position_stick_y = 0;

    private final Paint paint = new Paint();

    private STICK_STATE stick_state = STICK_STATE.NO_MOVEMENT;
    private CLICK_STATE click_state = CLICK_STATE.SINGLE;

    private List<AnalogStickListener> listeners = new ArrayList<>();
    private long timeLastClick = 0;

    private int touchID;
    private float touchStartX;
    private float touchStartY;
    private float touchX;
    private float touchY;

    protected String strStickSide = "L";

    private static double getMovementRadius(float x, float y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double getAngle(float way_x, float way_y) {
        // prevent divisions by zero for corner cases
        if (way_x == 0) {
            return way_y < 0 ? Math.PI : 0;
        } else if (way_y == 0) {
            if (way_x > 0) {
                return Math.PI * 3 / 2;
            } else if (way_x < 0) {
                return Math.PI * 1 / 2;
            }
        }
        // return correct calculated angle for each quadrant
        if (way_x > 0) {
            if (way_y < 0) {
                // first quadrant
                return 3 * Math.PI / 2 + Math.atan((double) (-way_y / way_x));
            } else {
                // second quadrant
                return Math.PI + Math.atan((double) (way_x / way_y));
            }
        } else {
            if (way_y > 0) {
                // third quadrant
                return Math.PI / 2 + Math.atan((double) (way_y / -way_x));
            } else {
                // fourth quadrant
                return 0 + Math.atan((double) (-way_x / -way_y));
            }
        }
    }

    public AnalogStickFree(VirtualController controller, Context context, int elementId) {
        super(controller, context, elementId);
        // reset stick position
        position_stick_x = getWidth() / 2;
        position_stick_y = getHeight() / 2;
    }

    public void addAnalogStickListener(AnalogStickListener listener) {
        listeners.add(listener);
    }

    private void notifyOnMovement(float x, float y) {
        _DBG("movement x: " + x + " movement y: " + y);
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onMovement(x, y);
        }
    }

    private void notifyOnClick() {
        _DBG("click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onClick();
        }
    }

    private void notifyOnDoubleClick() {
        _DBG("double click");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onDoubleClick();
        }
    }

    private void notifyOnRevoke() {
        _DBG("revoke");
        // notify listeners
        for (AnalogStickListener listener : listeners) {
            listener.onRevoke();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // calculate new radius sizes depending
        radius_complete = getPercent(getCorrectWidth() / 2, 100) - 2 * getDefaultStrokeWidth();

        int deadzonePercentage = PreferenceConfiguration.readPreferences(getContext()).virtualAnalogDeadzonePercentage;
        // calculate deadzone based of Preferences
        float minDeadzonePercent = 0f;
        float maxDeadzonePercent = 75f;
        float deadzonePercent = minDeadzonePercent + (maxDeadzonePercent - minDeadzonePercent) * (deadzonePercentage / 100f);

        radius_dead_zone = getPercent(getCorrectWidth() / 2, deadzonePercent);
        radius_analog_stick = getPercent(getCorrectWidth() / 2, 20);

        position_stick_x = w / 2f;
        position_stick_y = h / 2f;

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        // determine center point
        float centerX = bIsFingerOnScreen ? touchStartX : getWidth() / 2f;
        float centerY = bIsFingerOnScreen ? touchStartY : getHeight() / 2f;

        // determine stick position
        float handleX = bIsFingerOnScreen ? position_stick_x : centerX;
        float handleY = bIsFingerOnScreen ? position_stick_y : centerY;

        // draw outer circle
        if (!isPressed() || click_state == CLICK_STATE.SINGLE) {
            paint.setColor(getDefaultColor());
        } else {
            paint.setColor(pressedColor);
        }
        canvas.drawCircle(centerX, centerY, radius_complete, paint);

        paint.setColor(getDefaultColor());
        // draw dead zone
        canvas.drawCircle(centerX, centerY, radius_dead_zone, paint);

        // draw stick depending on state
        switch (stick_state) {
            case NO_MOVEMENT: {
                paint.setColor(getDefaultColor());
                canvas.drawCircle(centerX, centerY, radius_analog_stick, paint);
                break;
            }
            case MOVED_IN_DEAD_ZONE:
            case MOVED_ACTIVE: {
                paint.setColor(pressedColor);
                canvas.drawCircle(handleX, handleY, radius_analog_stick, paint);
                break;
            }
        }
    }

    private void updatePosition(long eventTime) {
        // get 100% way
        float complete = radius_complete - radius_analog_stick;

        // calculate relative way
        float correlated_y = (float) (Math.sin(Math.PI / 2 - movement_angle) * (movement_radius));
        float correlated_x = (float) (Math.cos(Math.PI / 2 - movement_angle) * (movement_radius));

        // update positions
        position_stick_x = touchStartX - correlated_x;
        position_stick_y = touchStartY - correlated_y;

        // Stay active even if we're back in the deadzone because we know the user is actively
        // giving analog stick input and we don't want to snap back into the deadzone.
        // We also release the deadzone if the user keeps the stick pressed for a bit to allow
        // them to make precise movements.
        stick_state = (stick_state == STICK_STATE.MOVED_ACTIVE ||
                eventTime - timeLastClick > timeoutDeadzone ||
                movement_radius > radius_dead_zone) ?
                STICK_STATE.MOVED_ACTIVE : STICK_STATE.MOVED_IN_DEAD_ZONE;

        //  trigger move event if state active
        if (stick_state == STICK_STATE.MOVED_ACTIVE) {
            notifyOnMovement(-correlated_x / complete, correlated_y / complete);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // save last click state
        CLICK_STATE lastClickState = click_state;

        // get absolute way for each axis
        relative_x = -(touchStartX - event.getX());
        relative_y = -(touchStartY - event.getY());

        // get radius and angel of movement from center
        movement_radius = getMovementRadius(relative_x, relative_y);
        movement_angle = getAngle(relative_x, relative_y);

        // pass touch event to parent if out of outer circle
//        if (movement_radius > radius_complete && !isPressed())
//            return false;

        // chop radius if out of outer circle or near the edge
        if (movement_radius > (radius_complete - radius_analog_stick)) {
            movement_radius = radius_complete - radius_analog_stick;
        }
        // handle event depending on action
        switch (event.getActionMasked()) {
            // down event (touch event)
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (!bIsFingerOnScreen) {
                    touchID = event.getPointerId(event.getActionIndex());
                    touchStartX = event.getX();
                    touchStartY = event.getY();

                    // set stick position to touchpoint
                    position_stick_x = touchStartX;
                    position_stick_y = touchStartY;

                    // reset relative movement
                    relative_x = 0;
                    relative_y = 0;
                    movement_radius = 0;
                    movement_angle = 0;

                    bIsFingerOnScreen = true;
                }

                if (touchID == event.getPointerId(event.getActionIndex())) {
                    touchX = event.getX();
                    touchY = event.getY();

                    stick_state = STICK_STATE.MOVED_IN_DEAD_ZONE;

                    if (lastClickState == CLICK_STATE.SINGLE &&
                            timeLastClick + timeoutDoubleClick > System.currentTimeMillis()) {
                        click_state = CLICK_STATE.DOUBLE;
                        notifyOnDoubleClick();
                    } else {
                        click_state = CLICK_STATE.SINGLE;
                        notifyOnClick();
                    }

                    timeLastClick = System.currentTimeMillis();
                    setPressed(true);

                    updatePosition(event.getEventTime());
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (touchID == event.getPointerId(i)) {
                        touchX = event.getX();
                        touchY = event.getY();

                        updatePosition(event.getEventTime());
                    }
                }
                break;
            }
            // up event (revoke touch)
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (touchID == event.getPointerId(event.getActionIndex())) {
                    setPressed(false);
                    bIsFingerOnScreen = false;
                }
                break;
            }
        }

        if (isPressed()) {
            updatePosition(event.getEventTime());
            // when is pressed calculate new positions (will trigger movement if necessary)
        } else {
            stick_state = STICK_STATE.NO_MOVEMENT;
            notifyOnRevoke();

            // not longer pressed reset analog stick
            notifyOnMovement(0, 0);
        }
        // refresh view
        invalidate();
        // accept the touch event
        return true;
    }
}
