/*
 * Copyright 2026 HushGram contributors
 * Redesigned native Instagram settings interface.
 */
package app.hushgram.extension.instagram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.BooleanSetting;

/**
 * Completely redesigned native settings interface matching Instagram's modern Android design.
 * Built with pure modern views (ScrollView + LinearLayout), full-width rows, monochrome glyphs,
 * native Instagram switches, subpage slide animations, and live search.
 */
public final class ModernSettingsView extends FrameLayout {
    private static final int COLOR_BG = 0xFF000000;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8E8E93;
    private static final int COLOR_TEXT_MUTED = 0xFF636366;
    private static final int COLOR_SEARCH_BG = 0xFF262626;
    private static final int COLOR_ACCENT = 0xFF0095F6;
    private static final int COLOR_SWITCH_OFF = 0xFF36363E;
    private static final int COLOR_DIVIDER = 0xFF1F1F1F;
    private static final int COLOR_RIPPLE = 0x18FFFFFF;

    private final SettingsDialog dialog;
    private final ScrollView overviewScroll;
    private final LinearLayout overviewContent;
    private final ScrollView subpageScroll;
    private final LinearLayout subpageContent;
    private final ScrollView searchScroll;
    private final LinearLayout searchContent;

    private final EditText searchField;
    private final Button searchClearButton;
    private String currentQuery = "";
    @Nullable private Category activeCategory;
    private int overviewScrollY = 0;

    private final List<Category> categories = new ArrayList<>();
    private final List<SettingItem> allItems = new ArrayList<>();

    public static final class SettingItem {
        final String title;
        final String summary;
        @Nullable final BooleanSetting setting;
        @Nullable final Runnable action;
        final String categoryTitle;

        SettingItem(String title, String summary, @Nullable BooleanSetting setting, @Nullable Runnable action, String categoryTitle) {
            this.title = title;
            this.summary = summary;
            this.setting = setting;
            this.action = action;
            this.categoryTitle = categoryTitle;
        }
    }

    public static final class Category {
        final String title;
        final String summary;
        final String iconName;
        final List<SettingItem> items = new ArrayList<>();

        Category(String title, String summary, String iconName) {
            this.title = title;
            this.summary = summary;
            this.iconName = iconName;
        }

        Category add(String title, String summary, BooleanSetting setting) {
            items.add(new SettingItem(title, summary, setting, null, this.title));
            return this;
        }

        Category addAction(String title, String summary, Runnable action) {
            items.add(new SettingItem(title, summary, null, action, this.title));
            return this;
        }
    }

    public ModernSettingsView(Context context, SettingsDialog dialog) {
        super(context);
        this.dialog = dialog;
        setBackgroundColor(COLOR_BG);

        initCategories();

        // 1. Overview Page
        overviewScroll = new ScrollView(context);
        overviewScroll.setVerticalScrollBarEnabled(false);
        overviewScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        overviewContent = new LinearLayout(context);
        overviewContent.setOrientation(LinearLayout.VERTICAL);
        overviewScroll.addView(overviewContent, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(overviewScroll, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Subpage Container
        subpageScroll = new ScrollView(context);
        subpageScroll.setVerticalScrollBarEnabled(false);
        subpageScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        subpageContent = new LinearLayout(context);
        subpageContent.setOrientation(LinearLayout.VERTICAL);
        subpageScroll.addView(subpageContent, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        subpageScroll.setVisibility(View.GONE);
        addView(subpageScroll, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. Search Results Container
        searchScroll = new ScrollView(context);
        searchScroll.setVerticalScrollBarEnabled(false);
        searchScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        searchContent = new LinearLayout(context);
        searchContent.setOrientation(LinearLayout.VERTICAL);
        searchScroll.addView(searchContent, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        searchScroll.setVisibility(View.GONE);
        addView(searchScroll, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Search bar at the top of overview
        View searchBar = createSearchBar(context);
        searchField = searchBar.findViewById(android.R.id.input);
        searchClearButton = searchBar.findViewById(android.R.id.button1);
        overviewContent.addView(searchBar);

        populateOverview();
    }

    private void initCategories() {
        categories.clear();
        allItems.clear();

        // 1. Privacy & Ghost Mode
        Category privacy = new Category(L10n.t("Privacy & Ghost Mode"),
                L10n.t("Ghost mode, stealth viewing, disable tracking"), SettingsIcons.BLOCK)
                .add(L10n.t("Ghost Mode for Direct Messages"), L10n.t("Read messages without marking them as seen"), Settings.READ_WITHOUT_SEEN_RECEIPT)
                .add(L10n.t("Stealth Story Viewing"), L10n.t("Watch stories without appearing in the viewer list"), Settings.VIEW_STORIES_ANONYMOUSLY)
                .add(L10n.t("Stealth Expiring Media"), L10n.t("Open disappearing photos and videos without marking as opened"), Settings.VIEW_DM_MEDIA_ANONYMOUSLY)
                .add(L10n.t("Stealth Live Broadcasts"), L10n.t("Join and watch live streams without alerting the host"), Settings.VIEW_LIVE_ANONYMOUSLY)
                .add(L10n.t("Stealth Blend Reels"), L10n.t("Watch shared reels in Blend without sending watch status"), Settings.HIDE_BLEND_VISITS)
                .add(L10n.t("Disable Typing Indicator"), L10n.t("Never send typing... status in direct messages"), Settings.HIDE_TYPING)
                .add(L10n.t("Mark Seen on Reply"), L10n.t("Mark messages as seen only when you reply to them"), Settings.MARK_SEEN_ON_REPLY)
                .add(L10n.t("Allow Screenshots"), L10n.t("Allow screenshots of disappearing messages"), Settings.ALLOW_SCREENSHOTS)
                .add(L10n.t("Clean Shared Links"), L10n.t("Remove tracking identifiers (igsh, utm) when sharing links"), Settings.SANITIZE_SHARING_LINKS);
        categories.add(privacy);

        // 2. Feed & Home
        Category feed = new Category(L10n.t("Feed & Home"),
                L10n.t("Hide ads, suggested posts, auto-play, links"), SettingsIcons.FEED)
                .add(L10n.t("Hide Sponsored Posts"), L10n.t("Completely remove all sponsored ads from your feed"), Settings.HIDE_ADS)
                .add(L10n.t("Hide Suggested Posts"), L10n.t("Remove 'Suggested for you' and recommended accounts"), Settings.HIDE_SUGGESTED_POSTS)
                .add(L10n.t("Open Following Feed by Default"), L10n.t("Automatically switch to chronological Following feed on launch"), Settings.START_ON_FOLLOWING)
                .add(L10n.t("Open Links in External Browser"), L10n.t("Open web links in your default browser instead of in-app"), Settings.OPEN_LINKS_EXTERNALLY)
                .add(L10n.t("Play Feed Videos With Sound"), L10n.t("Start feed videos unmuted by default"), Settings.START_FEED_VIDEOS_WITH_SOUND)
                .add(L10n.t("Tap Video to Pause"), L10n.t("Tap anywhere on a feed video post to toggle pause"), Settings.TAP_FEED_VIDEO_TO_PAUSE)
                .add(L10n.t("Feed Video Seek Bar"), L10n.t("Show an interactive scrub bar on feed video posts"), Settings.FEED_SEEK_BAR)
                .add(L10n.t("Long Press to Zoom"), L10n.t("Press and hold any feed photo or video to zoom in"), Settings.LONG_PRESS_TO_ZOOM)
                .add(L10n.t("Peek Carousel Pages"), L10n.t("Show preview edges of carousel pages"), Settings.PEEK_CAROUSEL_PAGES);
        categories.add(feed);

        // 3. Reels & Video
        Category reels = new Category(L10n.t("Reels & Video"),
                L10n.t("Auto-advance, loop controls, downloads"), SettingsIcons.REELS)
                .add(L10n.t("Auto-Advance Reels"), L10n.t("Automatically advance to next reel when video ends"), Settings.REEL_AUTO_ADVANCE)
                .add(L10n.t("Disable Reel Video Looping"), L10n.t("Prevent reels from repeating continuously"), Settings.STOP_VIDEO_LOOPING)
                .add(L10n.t("Interactive Reel Seek Bar"), L10n.t("Display scrub seek bar on reel videos"), Settings.REEL_SEEK_BAR)
                .add(L10n.t("Don't Send Reel Watch History"), L10n.t("Prevent logging watched reels to Meta servers"), Settings.DONT_SEND_REEL_WATCH_HISTORY)
                .add(L10n.t("Download Button on Reels"), L10n.t("Add a direct download button to the reels action bar"), Settings.REEL_DOWNLOAD_BUTTON)
                .add(L10n.t("Remember Full Screen Clear Mode"), L10n.t("Keep clear mode active when swiping between reels"), Settings.KEEP_REEL_CLEAR_MODE)
                .add(L10n.t("Remember Playback Speed"), L10n.t("Remember selected playback speed across reels"), Settings.KEEP_REEL_SPEED)
                .add(L10n.t("Hide Reels Tab"), L10n.t("Remove the Reels button from bottom navigation bar"), Settings.HIDE_REELS_TAB);
        categories.add(reels);

        // 4. Stories
        Category stories = new Category(L10n.t("Stories"),
                L10n.t("Stealth viewing, scrub bar, timestamps"), SettingsIcons.STORIES)
                .add(L10n.t("Loop Video Stories"), L10n.t("Repeat video stories until manually advanced"), Settings.LOOP_STORIES)
                .add(L10n.t("Smooth Story Transitions"), L10n.t("Enable smooth opening and closing animations for stories"), Settings.SMOOTH_STORY_OPEN)
                .add(L10n.t("Show Exact Story Timestamp"), L10n.t("Display exact time posted instead of relative hours"), Settings.SHOW_STORY_TIME)
                .add(L10n.t("Show Hidden Story Mentions"), L10n.t("Highlight hidden mentions and tags added to stories"), Settings.SHOW_STORY_MENTIONS)
                .add(L10n.t("Start Stories With Sound"), L10n.t("Always play story audio unmuted"), Settings.START_STORIES_WITH_SOUND)
                .add(L10n.t("Disable Swipe to Reply"), L10n.t("Prevent accidental reply input when swiping up"), Settings.STOP_STORY_SWIPE_REPLY)
                .add(L10n.t("Upload Stories at Full Quality"), L10n.t("Upload story photos and videos without compression"), Settings.UPLOAD_STORIES_AT_FULL_QUALITY)
                .add(L10n.t("Interactive Story Seek Bar"), L10n.t("Add a scrub bar at the bottom of video stories"), Settings.STORY_SEEK_BAR)
                .add(L10n.t("Download Button on Stories"), L10n.t("Add a quick download button to the story viewer header"), Settings.DOWNLOAD_BUTTON_ON_STORIES);
        categories.add(stories);

        // 5. Direct & Messages
        Category direct = new Category(L10n.t("Direct & Messages"),
                L10n.t("Call confirmations, hidden chats, history"), SettingsIcons.CHATS)
                .add(L10n.t("Confirm Before Calling"), L10n.t("Ask for confirmation before placing voice or video calls"), Settings.ASK_BEFORE_CALL)
                .add(L10n.t("Double Tap for Hidden Chats"), L10n.t("Double tap Direct title to open hidden secret chats"), Settings.DOUBLE_TAP_HIDDEN_CHATS)
                .add(L10n.t("Keep in Chat"), L10n.t("Stay inside conversation view when navigating back"), Settings.KEEP_IN_CHAT)
                .add(L10n.t("Hide Active Status"), L10n.t("Hide online status and last active indicator"), Settings.HIDE_ACTIVE_STATUS)
                .add(L10n.t("Preview Chats on Long Press"), L10n.t("Peek into conversations without marking as read"), Settings.PREVIEW_CHATS);
        categories.add(direct);

        // 6. Media & Downloads
        Category downloads = new Category(L10n.t("Media & Downloads"),
                L10n.t("Hold to download, download buttons, quality"), SettingsIcons.DOWNLOADS)
                .add(L10n.t("Hold to Download"), L10n.t("Press and hold posts or stories to save media to gallery"), Settings.HOLD_TO_DOWNLOAD)
                .add(L10n.t("Feed Post Download Button"), L10n.t("Show a direct download button under feed posts"), Settings.FEED_DOWNLOAD_BUTTON)
                .add(L10n.t("Download Button on Reels"), L10n.t("Quickly save reels with one tap"), Settings.REEL_DOWNLOAD_BUTTON)
                .add(L10n.t("Download Feed Videos"), L10n.t("Allow saving feed videos directly to your device"), Settings.DOWNLOAD_VIDEOS)
                .add(L10n.t("Download Feed Photos"), L10n.t("Allow saving feed images directly to your device"), Settings.DOWNLOAD_PHOTOS)
                .add(L10n.t("Download Profile Pictures"), L10n.t("Long press profile pictures to view and save in HD"), Settings.SAVE_PROFILE_PICTURES);
        categories.add(downloads);

        // 7. Glass Tab Bar & Navigation
        Category tabBar = new Category(L10n.t("Glass Tab Bar & Navigation"),
                L10n.t("Liquid glass bar, minimize on scroll, blur"), SettingsIcons.LAYOUT)
                .add(L10n.t("iOS Floating Glass Tab Bar"), L10n.t("Enable modern liquid glass bottom bar with backdrop blur"), Settings.GLASS_TAB_BAR)
                .add(L10n.t("Minimize Tab Bar While Scrolling"), L10n.t("Reduce tab bar size while scrolling down, expanding when scrolling up"), Settings.GLASS_TAB_BAR_MINIMIZE_ON_SCROLL)
                .add(L10n.t("Tab Bar Background Blur"), L10n.t("Real-time blur behind the floating tab bar (Android 12+)"), Settings.GLASS_TAB_BAR_BLUR)
                .add(L10n.t("Floating Island Position"), L10n.t("Float the tab bar above the bottom gesture navigation bar"), Settings.GLASS_TAB_BAR_FLOAT)
                .add(L10n.t("Haptic Feedback on Tab Switch"), L10n.t("Gentle subtle haptic tick when switching tabs"), Settings.GLASS_TAB_BAR_HAPTICS)
                .add(L10n.t("Liquid Capsule Shape"), L10n.t("Fully rounded pill capsule shape for the tab bar"), Settings.GLASS_TAB_BAR_LIQUID);
        categories.add(tabBar);

        // 8. Appearance & Customization
        Category appearance = new Category(L10n.t("Appearance & Interface"),
                L10n.t("Pure black AMOLED mode, hide Threads button"), SettingsIcons.APPEARANCE)
                .add(L10n.t("Pure Black AMOLED Mode"), L10n.t("True black #000000 background for OLED screens"), Settings.SETTINGS_BLACK_LOOK)
                .add(L10n.t("Hide Threads Button"), L10n.t("Remove Threads badge from profile and navigation"), Settings.HIDE_THREADS_BUTTON)
                .add(L10n.t("Dismiss Update Warnings"), L10n.t("Disable 'This version of Instagram has expired' dialogs"), Settings.REMOVE_BUILD_EXPIRED_POPUP);
        categories.add(appearance);

        // 9. Support & About
        Category about = new Category(L10n.t("About & Support"),
                L10n.t("Report a problem, support, versions, licenses"), SettingsIcons.ABOUT)
                .addAction(L10n.t("Report a Problem"), L10n.t("Open GitHub issues with your version info prefilled"), this::openReport)
                .addAction(L10n.t("Support Project"), L10n.t("Support Instagram Plus development"), this::openSupport)
                .addAction(L10n.t("Open Source Licenses"), L10n.t("GPL-3.0 and third-party notices"), this::openLicenses);
        categories.add(about);

        // Flatten all items for instant live search
        for (Category cat : categories) {
            allItems.addAll(cat.items);
        }
    }

    private void populateOverview() {
        Context context = getContext();
        // Section: How You Use Instagram
        overviewContent.addView(createSectionHeader(context, L10n.t("HOW YOU USE INSTAGRAM")));
        for (int i = 0; i < 4; i++) {
            overviewContent.addView(createCategoryRow(context, categories.get(i)));
        }

        // Section: Privacy & Who Can See Your Content
        overviewContent.addView(createSectionHeader(context, L10n.t("WHO CAN SEE YOUR CONTENT")));
        overviewContent.addView(createCategoryRow(context, categories.get(4))); // Direct

        // Section: App and Media
        overviewContent.addView(createSectionHeader(context, L10n.t("APP AND MEDIA")));
        overviewContent.addView(createCategoryRow(context, categories.get(5))); // Downloads

        // Section: Interface & Navigation
        overviewContent.addView(createSectionHeader(context, L10n.t("INTERFACE & NAVIGATION")));
        overviewContent.addView(createCategoryRow(context, categories.get(6))); // Tab Bar
        overviewContent.addView(createCategoryRow(context, categories.get(7))); // Appearance

        // Section: More Info & Support
        overviewContent.addView(createSectionHeader(context, L10n.t("MORE INFO AND SUPPORT")));
        overviewContent.addView(createCategoryRow(context, categories.get(8))); // About

        // Native Centered Footer
        overviewContent.addView(createFooter(context));
    }

    private View createSearchBar(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        FrameLayout wrapper = new FrameLayout(context);
        int padH = Math.round(16 * density);
        int padV = Math.round(10 * density);
        wrapper.setPaddingRelative(padH, padV, padH, padV);

        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int height = Math.round(42 * density);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_SEARCH_BG);
        bg.setCornerRadius(Math.round(10 * density));
        pill.setBackground(bg);
        pill.setPaddingRelative(Math.round(12 * density), 0, Math.round(8 * density), 0);

        // Search icon
        ImageView icon = new ImageView(context);
        int iconSize = Math.round(18 * density);
        Drawable glass = SettingsIcons.icon(context, SettingsIcons.SEARCH, COLOR_TEXT_SECONDARY);
        glass.setBounds(0, 0, iconSize, iconSize);
        icon.setImageDrawable(glass);
        pill.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        // Search field
        EditText input = new EditText(context);
        input.setId(android.R.id.input);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTextColor(COLOR_TEXT_PRIMARY);
        input.setHintTextColor(COLOR_TEXT_SECONDARY);
        input.setHint(L10n.t("Search"));
        input.setPaddingRelative(Math.round(10 * density), 0, Math.round(8 * density), 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        pill.addView(input, inputParams);

        // Clear button
        Button clear = new Button(context);
        clear.setId(android.R.id.button1);
        clear.setBackground(null);
        Drawable cross = SettingsIcons.icon(context, SettingsIcons.CLOSE, COLOR_TEXT_SECONDARY);
        cross.setBounds(0, 0, iconSize, iconSize);
        clear.setCompoundDrawablesRelative(cross, null, null, null);
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> input.setText(""));
        pill.addView(clear, new LinearLayout.LayoutParams(Math.round(36 * density), Math.round(36 * density)));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                onSearchChanged(s.toString());
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        wrapper.addView(pill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        return wrapper;
    }

    private View createSectionHeader(Context context, String title) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextColor(COLOR_TEXT_SECONDARY);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.setLetterSpacing(0.04f);
        header.setAllCaps(true);
        int padH = Math.round(16 * density);
        int padTop = Math.round(24 * density);
        int padBottom = Math.round(8 * density);
        header.setPaddingRelative(padH, padTop, padH, padBottom);
        return header;
    }

    private View createCategoryRow(Context context, Category category) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE),
                new ColorDrawable(Color.TRANSPARENT), new ColorDrawable(Color.WHITE));
        row.setBackground(ripple);
        int padH = Math.round(16 * density);
        int padV = Math.round(13 * density);
        row.setPaddingRelative(padH, padV, padH, padV);
        row.setMinimumHeight(Math.round(54 * density));

        // Icon (Monochrome glyph, no squircle)
        ImageView icon = new ImageView(context);
        int iconSize = Math.round(24 * density);
        Drawable glyph = SettingsIcons.icon(context, category.iconName, COLOR_TEXT_PRIMARY);
        glyph.setBounds(0, 0, iconSize, iconSize);
        icon.setImageDrawable(glyph);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(Math.round(16 * density));
        row.addView(icon, iconParams);

        // Text column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(category.title);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textCol.addView(title);

        TextView summary = new TextView(context);
        summary.setText(category.summary);
        summary.setTextColor(COLOR_TEXT_SECONDARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        summary.setLineSpacing(0, 1.15f);
        textCol.addView(summary);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(textCol, textParams);

        // Right forward chevron >
        ImageView chevron = new ImageView(context);
        Drawable chev = new ScreenColors.Chevron(0xFF737373, Math.round(1.8f * density), 0);
        chevron.setImageDrawable(chev);
        LinearLayout.LayoutParams chevParams = new LinearLayout.LayoutParams(Math.round(14 * density), Math.round(20 * density));
        row.addView(chevron, chevParams);

        row.setOnClickListener(v -> openCategory(category));
        return row;
    }

    private View createSettingRow(Context context, SettingItem item) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE),
                new ColorDrawable(Color.TRANSPARENT), new ColorDrawable(Color.WHITE));
        row.setBackground(ripple);
        int padH = Math.round(16 * density);
        int padV = Math.round(13 * density);
        row.setPaddingRelative(padH, padV, padH, padV);
        row.setMinimumHeight(Math.round(52 * density));

        // Text Column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(item.title);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textCol.addView(title);

        if (!TextUtils.isEmpty(item.summary)) {
            TextView summary = new TextView(context);
            summary.setText(item.summary);
            summary.setTextColor(COLOR_TEXT_SECONDARY);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            summary.setLineSpacing(0, 1.15f);
            textCol.addView(summary);
        }

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginEnd(Math.round(12 * density));
        row.addView(textCol, textParams);

        if (item.setting != null) {
            // Instagram Toggle Switch
            Switch toggle = new Switch(context);
            styleSwitch(toggle, density);
            toggle.setChecked(item.setting.savedValue());
            toggle.setOnCheckedChangeListener((btn, checked) -> {
                item.setting.save(checked);
                if (item.setting.rebootApp) {
                    dialog.showRestart(true);
                }
            });
            row.setOnClickListener(v -> toggle.toggle());
            row.addView(toggle, new LinearLayout.LayoutParams(Math.round(48 * density), Math.round(28 * density)));
        } else if (item.action != null) {
            // Action chevron
            ImageView chevron = new ImageView(context);
            Drawable chev = new ScreenColors.Chevron(0xFF737373, Math.round(1.8f * density), 0);
            chevron.setImageDrawable(chev);
            row.setOnClickListener(v -> item.action.run());
            row.addView(chevron, new LinearLayout.LayoutParams(Math.round(14 * density), Math.round(20 * density)));
        }
        return row;
    }

    private void styleSwitch(Switch toggle, float density) {
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(Math.round(22 * density), Math.round(22 * density));
        thumb.setColor(Color.WHITE);

        GradientDrawable track = new GradientDrawable();
        track.setShape(GradientDrawable.RECTANGLE);
        track.setCornerRadius(Math.round(14 * density));
        track.setSize(Math.round(46 * density), Math.round(26 * density));
        track.setColor(Color.WHITE);

        toggle.setThumbDrawable(thumb);
        toggle.setTrackDrawable(track);
        toggle.setSwitchMinWidth(Math.round(46 * density));
        toggle.setSplitTrack(false);

        int[][] states = {{-android.R.attr.state_enabled, android.R.attr.state_checked},
                {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{0x80FFFFFF, 0x80FFFFFF, Color.WHITE, Color.WHITE}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{0x800095F6, 0x8036363E, COLOR_ACCENT, COLOR_SWITCH_OFF}));
    }

    private View createFooter(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView footer = new TextView(context);
        footer.setText(L10f("Instagram Plus %1$s • Instagram %2$s",
                L10n.isolate(Utils.getPatchesReleaseVersion()), L10n.isolate(Utils.getAppVersionName())));
        footer.setTextColor(COLOR_TEXT_MUTED);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Math.round(28 * density), 0, Math.round(36 * density));
        return footer;
    }

    private static String L10f(String format, Object... args) {
        return String.format(Locale.getDefault(), format, args);
    }

    public void openCategory(Category category) {
        activeCategory = category;
        dialog.showTitle(category.title, false);
        overviewScrollY = overviewScroll.getScrollY();

        subpageContent.removeAllViews();
        subpageContent.addView(createSectionHeader(getContext(), category.title));
        for (SettingItem item : category.items) {
            subpageContent.addView(createSettingRow(getContext(), item));
        }

        float offset = 35 * getResources().getDisplayMetrics().density;
        overviewScroll.animate().cancel();
        overviewScroll.animate()
                .translationX(-offset)
                .alpha(0f)
                .setDuration(120)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    overviewScroll.setVisibility(View.GONE);
                    subpageScroll.setVisibility(View.VISIBLE);
                    subpageScroll.scrollTo(0, 0);
                    subpageScroll.setTranslationX(offset);
                    subpageScroll.setAlpha(0f);
                    subpageScroll.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(220)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    dialog.updateBackHandler();
                })
                .start();
    }

    public boolean closeCategory() {
        if (activeCategory == null) return false;
        activeCategory = null;
        dialog.showTitle(L10n.t("Instagram Plus"), false);

        float offset = 35 * getResources().getDisplayMetrics().density;
        subpageScroll.animate().cancel();
        subpageScroll.animate()
                .translationX(offset)
                .alpha(0f)
                .setDuration(120)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    subpageScroll.setVisibility(View.GONE);
                    overviewScroll.setVisibility(View.VISIBLE);
                    overviewScroll.scrollTo(0, overviewScrollY);
                    overviewScroll.setTranslationX(-offset);
                    overviewScroll.setAlpha(0f);
                    overviewScroll.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(220)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    dialog.updateBackHandler();
                })
                .start();
        return true;
    }

    public void openStartPage(@Nullable String page) {
        if (page == null || page.isEmpty()) return;
        for (Category cat : categories) {
            if (cat.title.equalsIgnoreCase(page) || cat.iconName.equalsIgnoreCase(page)) {
                openCategory(cat);
                return;
            }
        }
    }

    private void onSearchChanged(String query) {
        currentQuery = query.trim().toLowerCase(Locale.ROOT);
        if (currentQuery.isEmpty()) {
            searchScroll.setVisibility(View.GONE);
            if (activeCategory != null) {
                subpageScroll.setVisibility(View.VISIBLE);
                dialog.showTitle(activeCategory.title, false);
            } else {
                overviewScroll.setVisibility(View.VISIBLE);
                dialog.showTitle(L10n.t("Instagram Plus"), false);
            }
            dialog.updateBackHandler();
            return;
        }

        // Showing search results
        dialog.showTitle(L10n.t("Search"), false);
        overviewScroll.setVisibility(View.GONE);
        subpageScroll.setVisibility(View.GONE);
        searchScroll.setVisibility(View.VISIBLE);
        searchContent.removeAllViews();

        int matches = 0;
        for (SettingItem item : allItems) {
            boolean match = item.title.toLowerCase(Locale.ROOT).contains(currentQuery)
                    || item.summary.toLowerCase(Locale.ROOT).contains(currentQuery)
                    || item.categoryTitle.toLowerCase(Locale.ROOT).contains(currentQuery);
            if (match) {
                matches++;
                searchContent.addView(createSettingRow(getContext(), item));
            }
        }

        if (matches == 0) {
            float density = getContext().getResources().getDisplayMetrics().density;
            TextView empty = new TextView(getContext());
            empty.setText(L10n.t("No matching settings"));
            empty.setTextColor(COLOR_TEXT_SECONDARY);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Math.round(60 * density), 0, 0);
            searchContent.addView(empty);
        }
        dialog.updateBackHandler();
    }

    public boolean canGoBack() {
        return activeCategory != null || !currentQuery.isEmpty();
    }

    @Nullable
    public View getActiveBackView() {
        if (activeCategory != null) return subpageScroll;
        if (!currentQuery.isEmpty()) return searchScroll;
        return null;
    }

    public boolean handleBack() {
        if (!currentQuery.isEmpty()) {
            searchField.setText("");
            return true;
        }
        if (activeCategory != null) {
            return closeCategory();
        }
        return false;
    }

    private void openReport() {
        ReportProblemForm.open(getContext(), java.util.Collections.emptyList());
    }

    private void openSupport() {
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/srijal")));
        } catch (Exception ex) {
            Utils.showToastLong("https://ko-fi.com/srijal");
        }
    }

    private void openLicenses() {
        Activity act = dialog.getActivity();
        if (act != null) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(act);
            builder.setTitle(L10n.t("Licenses"));
            builder.setMessage(LicenseNotice.TEXT);
            builder.setPositiveButton(L10n.t("Close"), null);
            android.app.AlertDialog alert = builder.create();
            ScreenColors.dialog(alert);
            alert.show();
        }
    }
}
