package com.ddkkinf.cookbook;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int COLOR_STATUS_BG = 0xFFF8F9FA;
    private static final int COLOR_THREE_BUTTON_NAV_BG = 0xFF1E293B;
    private static final int COLOR_GESTURE_NAV_BG = 0xFFFFFFFF;

    private View navBarBackdropView;
    private boolean isThreeButtonNav = true;
    private int lastBackdropWidth = -1;
    private int lastBackdropHeight = -1;
    private int lastBackdropGravity = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KitchenAlarmPlugin.class);
        super.onCreate(savedInstanceState);
        setupSystemBarsAndInsets();
    }

    @Override
    public void onResume() {
        super.onResume();
        applySystemBarAppearance();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applySystemBarAppearance();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemBarAppearance();
        Window window = getWindow();
        if (window != null) {
            window.getDecorView().post(this::applySystemBarAppearance);
        }
    }

    private void setupSystemBarsAndInsets() {
        Window window = getWindow();
        if (window == null) return;

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.getDecorView().setBackgroundColor(COLOR_STATUS_BG);

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView == null || !(webView.getParent() instanceof ViewGroup)) {
            applySystemBarAppearance();
            return;
        }

        ViewGroup parentLayout = (ViewGroup) webView.getParent();
        parentLayout.setBackgroundColor(COLOR_STATUS_BG);

        if (navBarBackdropView == null) {
            navBarBackdropView = new View(this);
            navBarBackdropView.setClickable(false);
            navBarBackdropView.setFocusable(false);
            navBarBackdropView.setBackgroundColor(COLOR_THREE_BUTTON_NAV_BG);

            ViewGroup.LayoutParams backdropLp = createBackdropLayoutParams(parentLayout, ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
            parentLayout.addView(navBarBackdropView, backdropLp);
        }

        ViewCompat.setOnApplyWindowInsetsListener(parentLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            int maxNavPx = Math.max(navBars.bottom, Math.max(navBars.left, navBars.right));
            int maxTappableNavPx = Math.max(tappable.bottom, Math.max(tappable.left, tappable.right));
            int minButtonNavPx = Math.round(30f * getResources().getDisplayMetrics().density);

            isThreeButtonNav = maxNavPx > 0 && (
                maxTappableNavPx > 0 ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                maxNavPx >= minButtonNavPx
            );

            ViewGroup.LayoutParams rawWebLp = webView.getLayoutParams();
            if (rawWebLp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams webLp = (ViewGroup.MarginLayoutParams) rawWebLp;
                int targetBottom = keyboardVisible ? Math.max(ime.bottom, systemBars.bottom) : systemBars.bottom;
                if (
                    webLp.leftMargin != systemBars.left ||
                    webLp.topMargin != systemBars.top ||
                    webLp.rightMargin != systemBars.right ||
                    webLp.bottomMargin != targetBottom
                ) {
                    webLp.leftMargin = systemBars.left;
                    webLp.topMargin = systemBars.top;
                    webLp.rightMargin = systemBars.right;
                    webLp.bottomMargin = targetBottom;
                    webView.setLayoutParams(webLp);
                }
            }

            updateNavBackdropGeometry(parentLayout, navBars);
            applySystemBarAppearance();

            return new WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(0, 0, 0, 0)
                )
                .build();
        });

        ViewCompat.requestApplyInsets(parentLayout);
        window.getDecorView().post(this::applySystemBarAppearance);
    }

    private ViewGroup.LayoutParams createBackdropLayoutParams(ViewGroup parent, int width, int height, int gravity) {
        if (parent instanceof CoordinatorLayout) {
            CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(width, height);
            lp.gravity = gravity;
            return lp;
        }
        if (parent instanceof FrameLayout) {
            return new FrameLayout.LayoutParams(width, height, gravity);
        }
        return new ViewGroup.LayoutParams(width, height);
    }

    private void updateNavBackdropGeometry(ViewGroup parentLayout, Insets navBars) {
        if (navBarBackdropView == null) return;

        int targetColor = isThreeButtonNav ? COLOR_THREE_BUTTON_NAV_BG : COLOR_GESTURE_NAV_BG;
        navBarBackdropView.setBackgroundColor(targetColor);

        int width = 0;
        int height = 0;
        int gravity = Gravity.BOTTOM;

        if (navBars.bottom > 0) {
            width = ViewGroup.LayoutParams.MATCH_PARENT;
            height = navBars.bottom;
            gravity = Gravity.BOTTOM;
        } else if (navBars.right > 0) {
            width = navBars.right;
            height = ViewGroup.LayoutParams.MATCH_PARENT;
            gravity = Gravity.RIGHT;
        } else if (navBars.left > 0) {
            width = navBars.left;
            height = ViewGroup.LayoutParams.MATCH_PARENT;
            gravity = Gravity.LEFT;
        }

        if (height == 0 && width == 0) {
            if (navBarBackdropView.getVisibility() != View.GONE) {
                navBarBackdropView.setVisibility(View.GONE);
            }
            return;
        }

        if (navBarBackdropView.getVisibility() != View.VISIBLE) {
            navBarBackdropView.setVisibility(View.VISIBLE);
        }
        if (width != lastBackdropWidth || height != lastBackdropHeight || gravity != lastBackdropGravity) {
            lastBackdropWidth = width;
            lastBackdropHeight = height;
            lastBackdropGravity = gravity;
            navBarBackdropView.setLayoutParams(createBackdropLayoutParams(parentLayout, width, height, gravity));
        }
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarAppearance() {
        Window window = getWindow();
        if (window == null) return;

        View decorView = window.getDecorView();
        decorView.setBackgroundColor(COLOR_STATUS_BG);

        int navColor = isThreeButtonNav ? COLOR_THREE_BUTTON_NAV_BG : COLOR_GESTURE_NAV_BG;
        window.setStatusBarColor(COLOR_STATUS_BG);
        window.setNavigationBarColor(navColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
        if (controller != null) {
            // Top status bar sits on light #F8F9FA -> dark icons
            controller.setAppearanceLightStatusBars(true);
            // 3-button nav sits on dark slate #1E293B -> bright white icons (false);
            // Gesture pill sits on white #FFFFFF -> dark pill (true)
            controller.setAppearanceLightNavigationBars(!isThreeButtonNav);
        }
    }
}
