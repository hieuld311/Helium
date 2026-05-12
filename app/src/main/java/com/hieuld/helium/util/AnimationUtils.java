package com.hieuld.helium.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewAnimationUtils;

/**
 * Utility class for creating and managing View animations.
 */
public class AnimationUtils {

    /** Creates a circular reveal animation to show or hide a view. */
    public static Animator createCircularReveal(View view, int centerX, int centerY, float startRadius, float endRadius) {
        return ViewAnimationUtils.createCircularReveal(view, centerX, centerY, startRadius, endRadius);
    }

    /** Returns a listener that makes the view VISIBLE at the start of the animation. */
    public static Animator.AnimatorListener visibleBefore(final View view) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }
        };
    }

    /** Returns a listener that sets the view to GONE after the animation finishes. */
    public static Animator.AnimatorListener goneAfter(final View view) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        };
    }

    /** Returns a listener that sets the view to INVISIBLE after the animation finishes. */
    public static Animator.AnimatorListener invisibleAfter(final View view) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.INVISIBLE);
            }
        };
    }
}
