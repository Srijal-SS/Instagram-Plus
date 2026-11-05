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
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
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
import android.window.BackEvent;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.BooleanSetting;

/**
 * Completely redesigned native settings interface matching Instagram's modern Android design.
 * Built with pure modern views (ScrollView + LinearLayout), full-width rows, monochrome glyphs,
 * native Instagram switches, subpage slide transitions, edge-swipe back gestures, and instant live search.
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

    private float touchDownX;
    private float touchDownY;
    private boolean isSwipingBack;

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
                L10n.t("Ghost mode, stealth viewing, disable tracking, anti-screenshot"), SettingsIcons.BLOCK)
                .add(L10n.t("Ghost Mode for Direct Messages"), L10n.t("Read messages without marking them as seen"), Settings.READ_WITHOUT_SEEN_RECEIPT)
                .add(L10n.t("Keep Chats Unread Here"), L10n.t("Opened chats stay unread on this device too"), Settings.KEEP_CHATS_UNREAD)
                .add(L10n.t("Mark Seen on Reply"), L10n.t("Mark messages as seen only when you reply to them"), Settings.MARK_SEEN_ON_REPLY)
                .add(L10n.t("Mark Read in Chat Header"), L10n.t("Show a check mark in chat header to mark as seen"), Settings.MARK_READ_IN_CHAT)
                .add(L10n.t("Seen Receipt Per Chat"), L10n.t("Choose seen receipt behavior for each individual chat"), Settings.SEEN_PER_CHAT)
                .add(L10n.t("Disable Typing Indicator"), L10n.t("Never send typing... status in direct messages"), Settings.HIDE_TYPING)
                .add(L10n.t("Hide Active Status"), L10n.t("Hide online status and last active indicator"), Settings.HIDE_ACTIVE_STATUS)
                .add(L10n.t("Don't Report Screenshots"), L10n.t("Never alert others when you take a screenshot"), Settings.HIDE_SCREENSHOTS)
                .add(L10n.t("Don't Report Blend Visits"), L10n.t("Watch shared reels in Blend without sending watch status"), Settings.HIDE_BLEND_VISITS)
                .add(L10n.t("Allow Screenshots"), L10n.t("Allow screenshots of disappearing messages and media"), Settings.ALLOW_SCREENSHOTS)
                .add(L10n.t("Stealth Story Viewing"), L10n.t("Watch stories without appearing in the viewer list"), Settings.VIEW_STORIES_ANONYMOUSLY)
                .add(L10n.t("Stealth Expiring Media"), L10n.t("Open disappearing photos and videos without marking as opened"), Settings.VIEW_DM_MEDIA_ANONYMOUSLY)
                .add(L10n.t("Stealth Live Broadcasts"), L10n.t("Join and watch live streams without alerting the host"), Settings.VIEW_LIVE_ANONYMOUSLY)
                .add(L10n.t("Don't Send Reel Watch History"), L10n.t("Prevent logging watched reels to Meta servers"), Settings.DONT_SEND_REEL_WATCH_HISTORY)
                .add(L10n.t("Clean Shared Links"), L10n.t("Remove tracking identifiers (igsh, utm) when sharing links"), Settings.SANITIZE_SHARING_LINKS);
        categories.add(privacy);

        // 2. Feed & Home
        Category feed = new Category(L10n.t("Feed & Home"),
                L10n.t("Following feed, hide ads, suggestions, zoom, seek bar"), SettingsIcons.FEED)
                .add(L10n.t("Open Following Feed by Default"), L10n.t("Automatically switch to chronological Following feed on launch"), Settings.START_ON_FOLLOWING)
                .add(L10n.t("Only Accounts You Follow"), L10n.t("Keep home feed strictly to accounts you follow"), Settings.ONLY_FOLLOWING)
                .add(L10n.t("Instagram Logo on Home"), L10n.t("Keep Instagram logo visible even when Following is active"), Settings.LOGO_ON_FOLLOWING)
                .add(L10n.t("Fall Back to Following"), L10n.t("Show Following feed when main feed is unavailable"), Settings.FOLLOWING_FALLBACK)
                .add(L10n.t("Mix in Following"), L10n.t("Mix accounts you follow into the feed when recommendations end"), Settings.FOLLOWING_MIX)
                .add(L10n.t("Hide Sponsored Posts & Ads"), L10n.t("Completely remove all sponsored ads from your feed"), Settings.HIDE_ADS)
                .add(L10n.t("Hide Suggested Accounts"), L10n.t("Remove suggested account recommendation cards"), Settings.HIDE_SUGGESTED_ACCOUNTS)
                .add(L10n.t("Hide Suggested Posts"), L10n.t("Remove 'Suggested for you' post recommendations"), Settings.HIDE_SUGGESTED_POSTS)
                .add(L10n.t("Hide Threads Posts"), L10n.t("Remove Threads previews and recommendations from feed"), Settings.HIDE_THREADS_POSTS)
                .add(L10n.t("Hide Surveys"), L10n.t("Remove feed surveys and polls"), Settings.HIDE_FEED_SURVEYS)
                .add(L10n.t("Hide Shopping Units"), L10n.t("Remove shopping and product units from feed"), Settings.HIDE_FEED_SHOPPING)
                .add(L10n.t("Hide 'Are You Interested?'"), L10n.t("Remove interest bubbles and query cards"), Settings.HIDE_FEED_INTEREST)
                .add(L10n.t("Hide Caught Up Card"), L10n.t("Hide 'You're all caught up' indicator card"), Settings.HIDE_CAUGHT_UP_CARD)
                .add(L10n.t("Hide Posts You've Liked"), L10n.t("Filter out posts you have already liked"), Settings.HIDE_FEED_LIKED)
                .add(L10n.t("Open Links in External Browser"), L10n.t("Open web links in your default browser instead of in-app"), Settings.OPEN_LINKS_EXTERNALLY)
                .add(L10n.t("Full Resolution Photos"), L10n.t("Load feed photos in original high resolution"), Settings.FULL_RESOLUTION_PHOTOS)
                .add(L10n.t("Ask for Larger Photos"), L10n.t("Request larger photo assets from Instagram servers"), Settings.ASK_FOR_LARGER_PHOTOS)
                .add(L10n.t("Full Size Covers"), L10n.t("Load full resolution covers for feed videos"), Settings.FULL_SIZE_COVERS)
                .add(L10n.t("Confirm Likes"), L10n.t("Ask confirmation before liking a post"), Settings.ASK_BEFORE_LIKE)
                .add(L10n.t("Confirm Feed Refresh"), L10n.t("Ask confirmation before refreshing the feed"), Settings.ASK_BEFORE_REFRESH)
                .add(L10n.t("Show Exact Post Timestamp"), L10n.t("Display exact time posted instead of relative hours"), Settings.SHOW_POST_TIME)
                .add(L10n.t("Show Hidden Like Counts"), L10n.t("Reveal like counts hidden by post author"), Settings.SHOW_HIDDEN_LIKE_COUNTS)
                .add(L10n.t("Play Feed Videos With Sound"), L10n.t("Start feed videos unmuted by default"), Settings.START_FEED_VIDEOS_WITH_SOUND)
                .add(L10n.t("Tap Video to Pause"), L10n.t("Tap anywhere on a feed video post to toggle pause"), Settings.TAP_FEED_VIDEO_TO_PAUSE)
                .add(L10n.t("Feed Video Seek Bar"), L10n.t("Show an interactive scrub bar on feed video posts"), Settings.FEED_SEEK_BAR)
                .add(L10n.t("Long Press to Zoom"), L10n.t("Press and hold any feed photo or video to zoom in"), Settings.LONG_PRESS_TO_ZOOM)
                .add(L10n.t("Rounded Post Corners"), L10n.t("Apply modern rounded corners to post media"), Settings.ROUNDED_POST_CORNERS)
                .add(L10n.t("Peek Carousel Pages"), L10n.t("Show preview edges of adjacent carousel pages"), Settings.PEEK_CAROUSEL_PAGES)
                .add(L10n.t("Carousel Navigation Arrows"), L10n.t("Show left and right navigation arrows on carousels"), Settings.CAROUSEL_ARROWS)
                .add(L10n.t("Carousel Dots Pill"), L10n.t("Encase carousel page indicator dots in a subtle capsule"), Settings.CAROUSEL_DOTS_PILL)
                .add(L10n.t("Ambient Light Behind Posts"), L10n.t("Soft ambient glow matching media colors behind posts"), Settings.AMBIENT_LIGHT);
        categories.add(feed);

        // 3. Reels & Video
        Category reels = new Category(L10n.t("Reels & Video"),
                L10n.t("Auto-advance, seek bar, loop controls, clear mode, speed"), SettingsIcons.REELS)
                .add(L10n.t("Auto-Advance Reels"), L10n.t("Automatically advance to next reel when video ends"), Settings.REEL_AUTO_ADVANCE)
                .add(L10n.t("Disable Reel Video Looping"), L10n.t("Prevent reels from repeating continuously"), Settings.STOP_VIDEO_LOOPING)
                .add(L10n.t("Interactive Reel Seek Bar"), L10n.t("Display scrub seek bar on reel videos"), Settings.REEL_SEEK_BAR)
                .add(L10n.t("Thicker Reel Seek Bar"), L10n.t("Increase height of seek bar for easier scrubbing"), Settings.BIG_REEL_SEEK_BAR)
                .add(L10n.t("Seek Bar on Chat Reels"), L10n.t("Show seek bar on reels shared inside direct messages"), Settings.DM_REEL_SEEK_BAR)
                .add(L10n.t("Show Exact Reel Post Time"), L10n.t("Display exact timestamp of when reel was posted"), Settings.SHOW_REEL_POST_TIME)
                .add(L10n.t("Show Seconds in Post Time"), L10n.t("Include exact seconds in reel post timestamp"), Settings.REEL_POST_TIME_SECONDS)
                .add(L10n.t("Download Button on Reels"), L10n.t("Add a direct download button to the reels action bar"), Settings.REEL_DOWNLOAD_BUTTON)
                .add(L10n.t("Remember Full Screen Clear Mode"), L10n.t("Keep clear mode active when swiping between reels"), Settings.KEEP_REEL_CLEAR_MODE)
                .add(L10n.t("Remember Playback Speed"), L10n.t("Remember selected playback speed across reels"), Settings.KEEP_REEL_SPEED)
                .add(L10n.t("Speed Button on Reels"), L10n.t("Add quick playback speed button to reels"), Settings.SPEED_BUTTON_ON_REELS)
                .add(L10n.t("Full Screen Button on Reels"), L10n.t("Add one-tap full screen button to reels"), Settings.FULL_SCREEN_BUTTON_ON_REELS)
                .add(L10n.t("Copy Text Button on Reels"), L10n.t("Add button to copy reel captions and text"), Settings.TEXT_BUTTON_ON_REELS)
                .add(L10n.t("Not Interested Button on Reels"), L10n.t("Add quick 'Not interested' button on reels"), Settings.REEL_NOT_INTERESTED_BUTTON)
                .add(L10n.t("Block Account Button on Reels"), L10n.t("Add quick block button on reels"), Settings.REEL_BLOCK_BUTTON)
                .add(L10n.t("Hide Reels Tab"), L10n.t("Remove the Reels button from bottom navigation bar"), Settings.HIDE_REELS_TAB)
                .add(L10n.t("Stop Reels Scrolling"), L10n.t("Prevent accidental scrolling while watching a reel"), Settings.STOP_REELS_SCROLLING)
                .add(L10n.t("Swipe Reel to Open Profile"), L10n.t("Swipe left on reel to visit creator's profile"), Settings.REEL_SWIPE_TO_PROFILE)
                .add(L10n.t("Pinch to Zoom Reels"), L10n.t("Use pinch gesture to zoom in on reel videos"), Settings.PINCH_ZOOM_REELS)
                .add(L10n.t("Blur Bars Above/Below Reel"), L10n.t("Blur letterbox black bars with video colors"), Settings.BLUR_REEL_BARS);
        categories.add(reels);

        // 4. Stories
        Category stories = new Category(L10n.t("Stories"),
                L10n.t("Stealth viewing, scrub bar, timestamps, full quality"), SettingsIcons.STORIES)
                .add(L10n.t("Loop Video Stories"), L10n.t("Repeat video stories until manually advanced"), Settings.LOOP_STORIES)
                .add(L10n.t("Smooth Story Transitions"), L10n.t("Enable smooth opening and closing animations for stories"), Settings.SMOOTH_STORY_OPEN)
                .add(L10n.t("Stop Story Auto-Advance"), L10n.t("Pause automatically and wait for manual tap to advance"), Settings.BLOCK_STORY_AUTO_ADVANCE)
                .add(L10n.t("Show Exact Story Timestamp"), L10n.t("Display exact time posted instead of relative hours"), Settings.SHOW_STORY_TIME)
                .add(L10n.t("Show Hidden Story Mentions"), L10n.t("Highlight hidden mentions and tags added to stories"), Settings.SHOW_STORY_MENTIONS)
                .add(L10n.t("Red Story Mentions"), L10n.t("Highlight discovered hidden mentions in distinct red"), Settings.RED_STORY_MENTIONS)
                .add(L10n.t("Start Stories With Sound"), L10n.t("Always play story audio unmuted"), Settings.START_STORIES_WITH_SOUND)
                .add(L10n.t("Disable Swipe to Reply"), L10n.t("Prevent accidental reply input when swiping up"), Settings.STOP_STORY_SWIPE_REPLY)
                .add(L10n.t("Hide Story Reply Box"), L10n.t("Remove the bottom message input field on stories"), Settings.HIDE_STORY_REPLY_BOX)
                .add(L10n.t("Upload Stories at Full Quality"), L10n.t("Upload story photos and videos without compression"), Settings.UPLOAD_STORIES_AT_FULL_QUALITY)
                .add(L10n.t("Interactive Story Seek Bar"), L10n.t("Add a scrub bar at the bottom of video stories"), Settings.STORY_SEEK_BAR)
                .add(L10n.t("Speed Button on Stories"), L10n.t("Add playback speed toggle button to story viewer"), Settings.STORY_SPEED_BUTTON)
                .add(L10n.t("Stories Fill Screen"), L10n.t("Expand story media to completely fill aspect ratio"), Settings.FILL_STORY_SCREEN)
                .add(L10n.t("Ambient Light in Stories"), L10n.t("Soft ambient color blur behind letterboxed stories"), Settings.AMBIENT_LIGHT_STORIES)
                .add(L10n.t("Download Button on Stories"), L10n.t("Add a quick download button to the story viewer header"), Settings.DOWNLOAD_BUTTON_ON_STORIES)
                .add(L10n.t("Copy Text Button on Stories"), L10n.t("Add button to copy text overlays from stories"), Settings.TEXT_BUTTON_ON_STORIES)
                .add(L10n.t("Mark as Seen Button on Stories"), L10n.t("Add a manual mark as seen button while stealth viewing"), Settings.MARK_STORIES_SEEN)
                .add(L10n.t("Gray Out Watched Stories"), L10n.t("Dim tray circles of stories you have watched"), Settings.GRAY_OUT_WATCHED_STORIES)
                .add(L10n.t("Hide Suggested Stories"), L10n.t("Remove suggested stories between friend stories"), Settings.HIDE_SUGGESTED_STORIES)
                .add(L10n.t("Hide Story Rewinds"), L10n.t("Hide throwback and rewind cards in story tray"), Settings.HIDE_STORY_REWINDS)
                .add(L10n.t("Hide Memories and Recaps"), L10n.t("Hide memory reels and recap prompts"), Settings.HIDE_STORY_RECAPS)
                .add(L10n.t("Hide Stories Tray"), L10n.t("Remove the entire story carousel from home feed"), Settings.HIDE_STORIES_TRAY)
                .add(L10n.t("Keep Tray in Feed"), L10n.t("Keep story tray docked at top of feed while scrolling"), Settings.KEEP_TRAY_IN_FEED)
                .add(L10n.t("Filter Stories Tray by Verified"), L10n.t("Show only verified accounts in story tray"), Settings.FILTER_TRAY_BY_BADGE);
        categories.add(stories);

        // 5. Direct & Messages
        Category direct = new Category(L10n.t("Direct & Messages"),
                L10n.t("Call confirmation, hidden chats, history, lock, jump"), SettingsIcons.CHATS)
                .add(L10n.t("Confirm Before Calling"), L10n.t("Ask for confirmation before placing voice or video calls"), Settings.ASK_BEFORE_CALL)
                .add(L10n.t("Hide Call Buttons in Chats"), L10n.t("Remove call icons from top bar of chats"), Settings.HIDE_CALL_BUTTONS)
                .add(L10n.t("Double Tap for Hidden Chats"), L10n.t("Double tap Direct title to open hidden secret chats"), Settings.DOUBLE_TAP_HIDDEN_CHATS)
                .add(L10n.t("Keep in Chat"), L10n.t("Stay inside conversation view when navigating back"), Settings.KEEP_IN_CHAT)
                .add(L10n.t("Preview Chats on Long Press"), L10n.t("Peek into conversations without marking as read"), Settings.PREVIEW_CHATS)
                .add(L10n.t("Mark One-Time Photos & Videos"), L10n.t("Mark disappearing media so it doesn't vanish"), Settings.MARK_VIEW_ONCE)
                .add(L10n.t("Swipe Through Chat's Reels"), L10n.t("Swipe vertically through reels shared in a chat"), Settings.SWIPE_CHAT_REELS)
                .add(L10n.t("Stop After Chat's Reels"), L10n.t("Don't continue scrolling into algorithmic reels"), Settings.STOP_AFTER_CHAT_REELS)
                .add(L10n.t("Only Received Reels"), L10n.t("Swipe only through reels sent by the other person"), Settings.ONLY_RECEIVED_CHAT_REELS)
                .add(L10n.t("Jump to First Message or Date"), L10n.t("Quickly jump back in chat history to first message or date"), Settings.JUMP_IN_CHAT)
                .add(L10n.t("Export Chat as Text"), L10n.t("Export conversation messages to a text file"), Settings.EXPORT_CHAT)
                .add(L10n.t("Hide Notes Row in Inbox"), L10n.t("Remove the top horizontal notes bubble tray"), Settings.HIDE_NOTES_ROW)
                .add(L10n.t("Hide Map Bubble in Notes"), L10n.t("Remove the Map icon from notes header"), Settings.HIDE_NOTES_MAP)
                .add(L10n.t("Hide Inbox Search Bar"), L10n.t("Remove search field at top of messages inbox"), Settings.HIDE_INBOX_SEARCH)
                .add(L10n.t("Hide Inbox Suggestions"), L10n.t("Remove suggested contacts and channels"), Settings.HIDE_INBOX_SUGGESTIONS)
                .add(L10n.t("Hide Instants"), L10n.t("Remove instant games and shortcuts from inbox"), Settings.HIDE_INSTANTS)
                .add(L10n.t("Glass in Chats"), L10n.t("Enable translucent glass aesthetic in chat header and input"), Settings.GLASS_CHAT)
                .add(L10n.t("Full Screen Messages"), L10n.t("Maximize chat view area to edge-to-edge"), Settings.FULL_SCREEN_MESSAGES)
                .add(L10n.t("Lock Your Messages"), L10n.t("Require fingerprint or biometric lock to open Direct"), Settings.LOCK_MESSAGES)
                .add(L10n.t("Lock All of Instagram"), L10n.t("Require biometric authentication to open Instagram"), Settings.LOCK_APP);
        categories.add(direct);

        // 6. Comments
        Category comments = new Category(L10n.t("Comments"),
                L10n.t("Copy comments, select text, save photos, keyboard"), SettingsIcons.COMMENTS)
                .add(L10n.t("Copy Comments"), L10n.t("Long press or tap to copy entire comment text"), Settings.COPY_COMMENTS)
                .add(L10n.t("Copy Without @Mentions"), L10n.t("Strip recipient @usernames when copying comment"), Settings.COPY_COMMENTS_WITHOUT_MENTIONS)
                .add(L10n.t("Select Text in Comments"), L10n.t("Allow text selection cursor inside comment text"), Settings.SELECT_TEXT)
                .add(L10n.t("Copy Commenter's Username"), L10n.t("Quickly copy the handle of the person commenting"), Settings.COPY_COMMENT_AUTHORS)
                .add(L10n.t("Save Comment Photo"), L10n.t("Long press to save photos attached in comments"), Settings.SAVE_COMMENT_PHOTOS)
                .add(L10n.t("Reply With Comment's GIF"), L10n.t("Allow responding with animated GIFs in comment threads"), Settings.REPLY_WITH_COMMENT_GIFS)
                .add(L10n.t("Hide Comments"), L10n.t("Remove comments button and sheet entirely"), Settings.HIDE_COMMENTS)
                .add(L10n.t("Hide Emoji Row"), L10n.t("Remove quick emoji reactions bar above keyboard"), Settings.HIDE_COMMENT_EMOJI_ROW)
                .add(L10n.t("Open Comments Without Keyboard"), L10n.t("Keep keyboard closed when opening comment section"), Settings.COMMENT_KEYBOARD_DOWN);
        categories.add(comments);

        // 7. Explore & Search
        Category explore = new Category(L10n.t("Explore & Search"),
                L10n.t("Explore grid, search history, suggestions, personalization"), SettingsIcons.EXPLORE)
                .add(L10n.t("Hide Explore Grid"), L10n.t("Blank out the explore media grid for distraction-free use"), Settings.HIDE_EXPLORE_GRID)
                .add(L10n.t("Hide Suggested Accounts in Search"), L10n.t("Remove recommended accounts when typing in search"), Settings.HIDE_SEARCH_SUGGESTIONS)
                .add(L10n.t("Don't Save Recent Searches"), L10n.t("Never save searched terms to account search history"), Settings.DONT_SAVE_RECENT_SEARCHES)
                .add(L10n.t("Show All Recent Searches"), L10n.t("Expand recent searches list without truncation"), Settings.SHOW_ALL_RECENT_SEARCHES)
                .add(L10n.t("Explore Without Personalization"), L10n.t("Disable algorithmic personalization in Explore"), Settings.EXPLORE_WITHOUT_PERSONALIZATION)
                .add(L10n.t("Hide Explore's Menu"), L10n.t("Remove personalized category chips from top of Explore"), Settings.HIDE_EXPLORE_MENU);
        categories.add(explore);

        // 8. Media & Downloads
        Category downloads = new Category(L10n.t("Media & Downloads"),
                L10n.t("Hold to download, download buttons, HD photos/videos, quality"), SettingsIcons.DOWNLOADS)
                .add(L10n.t("Hold to Download"), L10n.t("Press and hold posts or stories to save media to gallery"), Settings.HOLD_TO_DOWNLOAD)
                .add(L10n.t("Download Feed Videos"), L10n.t("Allow saving feed videos directly to your device"), Settings.DOWNLOAD_VIDEOS)
                .add(L10n.t("Download Video Covers"), L10n.t("Option to save thumbnail cover art for videos"), Settings.DOWNLOAD_FEED_COVER)
                .add(L10n.t("Download Feed Photos"), L10n.t("Allow saving feed images directly to your device"), Settings.DOWNLOAD_PHOTOS)
                .add(L10n.t("Details in Post Menu"), L10n.t("Show exact media resolution, bitrate and format in menu"), Settings.POST_DETAILS)
                .add(L10n.t("Choose Carousel Pages"), L10n.t("Prompt to select specific photos/videos when downloading carousels"), Settings.DOWNLOAD_CHOOSE_PAGES)
                .add(L10n.t("Feed Post Download Button"), L10n.t("Show a direct download button under feed posts"), Settings.FEED_DOWNLOAD_BUTTON)
                .add(L10n.t("Save Every Page Without Asking"), L10n.t("Instantly download all pages of a carousel with one tap"), Settings.FEED_DOWNLOAD_ALL_PAGES)
                .add(L10n.t("Download Button on Reels"), L10n.t("Quickly save reels with one tap from the action bar"), Settings.REEL_DOWNLOAD_BUTTON)
                .add(L10n.t("Download Button on Stories"), L10n.t("Quickly save stories with one tap from the viewer header"), Settings.DOWNLOAD_BUTTON_ON_STORIES)
                .add(L10n.t("Save Profile Picture"), L10n.t("Long press profile pictures to view and save in original HD"), Settings.SAVE_PROFILE_PICTURES)
                .add(L10n.t("View Profile Picture"), L10n.t("Tap profile avatars to open in full screen preview"), Settings.VIEW_PROFILE_PICTURES)
                .add(L10n.t("Copy Username and Bio"), L10n.t("Long press bio to copy profile text and links"), Settings.COPY_PROFILE_TEXT)
                .add(L10n.t("Download Voice Messages"), L10n.t("Save audio voice notes from direct messages"), Settings.DOWNLOAD_VOICE_MESSAGES)
                .add(L10n.t("Open in Another Player"), L10n.t("Send video directly to external video player"), Settings.OPEN_IN_PLAYER)
                .add(L10n.t("Send Downloads to Another App"), L10n.t("Open share sheet immediately after saving media"), Settings.SEND_DOWNLOADS_TO_APP)
                .add(L10n.t("Save Compatible Videos"), L10n.t("Ensure saved videos use widely supported MP4 encoding"), Settings.DOWNLOAD_COMPATIBLE)
                .add(L10n.t("Convert to Keep Full Size"), L10n.t("Preserve original dimensions when converting media"), Settings.DOWNLOAD_CONVERT)
                .add(L10n.t("Never Save AV1 Video"), L10n.t("Avoid AV1 codec files for maximum device compatibility"), Settings.DOWNLOAD_NO_AV1)
                .add(L10n.t("Never Save HDR Video"), L10n.t("Convert HDR clips to SDR to prevent washed out colors"), Settings.DOWNLOAD_NO_HDR)
                .add(L10n.t("Folder Per Account"), L10n.t("Organize saved downloads into subfolders named after creator"), Settings.SAVE_FOLDER_PER_ACCOUNT)
                .add(L10n.t("Name Saves by Account and Post Time"), L10n.t("Format filenames as @username_YYYY-MM-DD"), Settings.SAVE_NAME_BY_POST)
                .add(L10n.t("Add Source to Media Metadata"), L10n.t("Embed post URL and author in EXIF metadata"), Settings.SAVE_PHOTO_METADATA)
                .add(L10n.t("Skip What's Already Saved"), L10n.t("Don't re-download files already present in gallery"), Settings.DOWNLOAD_SKIP_SAVED);
        categories.add(downloads);

        // 9. Profiles & Followers
        Category profiles = new Category(L10n.t("Profiles & Followers"),
                L10n.t("Follow status, unfollowers tracker, suggestions, clean profile"), SettingsIcons.PERSON)
                .add(L10n.t("Show If a Profile Follows You"), L10n.t("Display 'Follows you' badge on user profiles"), Settings.SHOW_FRIENDSHIP_STATUS)
                .add(L10n.t("Mark Who Doesn't Follow Back"), L10n.t("Highlight non-followers in your Following list"), Settings.MARK_FOLLOWING_LIST)
                .add(L10n.t("Put Non-Followers First"), L10n.t("Sort your Following list with accounts not following back at top"), Settings.FOLLOWING_NOT_BACK_FIRST)
                .add(L10n.t("Track Recent Unfollowers"), L10n.t("Monitor accounts that unfollow you locally"), Settings.TRACK_UNFOLLOWERS)
                .add(L10n.t("Notify Me of Unfollowers"), L10n.t("Send notification when someone unfollows your account"), Settings.NOTIFY_UNFOLLOWS)
                .add(L10n.t("Hide Suggested People"), L10n.t("Remove 'Suggested for you' carousels on profiles"), Settings.HIDE_PROFILE_SUGGESTIONS)
                .add(L10n.t("Hide Follow List Suggestions"), L10n.t("Remove suggested users from follower lists"), Settings.HIDE_FOLLOW_LIST_SUGGESTIONS)
                .add(L10n.t("Hide Highlights on Profiles"), L10n.t("Remove story highlights circles from profile pages"), Settings.HIDE_HIGHLIGHTS)
                .add(L10n.t("Saved Tab on Your Profile"), L10n.t("Add a direct Saved posts tab to your profile grid"), Settings.SAVED_ON_PROFILE)
                .add(L10n.t("Hide Threads Button"), L10n.t("Remove Threads badge from profile and navigation"), Settings.HIDE_THREADS_BUTTON)
                .add(L10n.t("Show Profile Posts as List"), L10n.t("Display profile grid posts in single-column vertical list"), Settings.PROFILE_POSTS_LIST);
        categories.add(profiles);

        // 10. Sharing & Reposts
        Category sharing = new Category(L10n.t("Sharing & Reposts"),
                L10n.t("Share sheet buttons, repost button, share button"), SettingsIcons.NETWORK)
                .add(L10n.t("Hide Group Buttons"), L10n.t("Remove group chat targets from share sheet"), Settings.HIDE_SHARE_SHEET_GROUP)
                .add(L10n.t("Hide Repost Button"), L10n.t("Remove Repost button under posts"), Settings.HIDE_REPOST_BUTTON)
                .add(L10n.t("Hide Share Button"), L10n.t("Remove Share paper airplane button under posts"), Settings.HIDE_SHARE_BUTTON);
        categories.add(sharing);

        // 11. Notifications
        Category notifications = new Category(L10n.t("Notifications"),
                L10n.t("Group notifications, featured section, badges"), SettingsIcons.NOTIFICATIONS)
                .add(L10n.t("Group Notifications"), L10n.t("Organize alerts by day and category"), Settings.GROUP_NOTIFICATIONS)
                .add(L10n.t("Group by Type"), L10n.t("Group likes, comments, and follows into separate sections"), Settings.GROUP_NOTIFICATIONS_BY_TYPE)
                .add(L10n.t("Hide Follow Back Suggestions"), L10n.t("Remove 'Follow back' cards from notification feed"), Settings.HIDE_FOLLOW_BACK_SUGGESTIONS)
                .add(L10n.t("Hide Notification Suggestions"), L10n.t("Remove recommended content from activity feed"), Settings.HIDE_NOTIFICATION_SUGGESTIONS)
                .add(L10n.t("Hide Featured Section"), L10n.t("Remove Featured alerts block at top of notifications"), Settings.HIDE_NOTIFICATION_FEATURED)
                .add(L10n.t("Hide Featured Red Dot"), L10n.t("Remove unread dot badge on Featured section"), Settings.HIDE_FEATURED_DOT);
        categories.add(notifications);

        // 12. Meta AI
        Category metaAi = new Category(L10n.t("Meta AI"),
                L10n.t("Hide Meta AI from search, chats, feed, reels, and profiles"), SettingsIcons.AI)
                .add(L10n.t("Hide Meta AI in Search"), L10n.t("Remove Meta AI search prompts and header buttons"), Settings.HIDE_META_AI_SEARCH)
                .add(L10n.t("Hide Meta AI Posts"), L10n.t("Filter out generated Meta AI posts from feed"), Settings.HIDE_META_AI_POSTS)
                .add(L10n.t("Hide About This Reel"), L10n.t("Remove AI-generated 'About this reel' descriptions"), Settings.HIDE_ABOUT_THIS_REEL)
                .add(L10n.t("Hide Ask Meta AI"), L10n.t("Remove 'Ask Meta AI' prompt button in reels"), Settings.HIDE_ASK_META_AI)
                .add(L10n.t("Hide Meta AI in Share Sheet"), L10n.t("Remove Meta AI shortcut from recipient share options"), Settings.HIDE_META_AI_SHARE_TARGET)
                .add(L10n.t("Hide Muse Card on Profiles"), L10n.t("Remove Meta AI Muse banner from profile headers"), Settings.HIDE_META_AI_PROFILE_BANNER)
                .add(L10n.t("Hide Muse Button on Profile"), L10n.t("Remove Muse AI button from your profile"), Settings.HIDE_META_AI_PROFILE_BUTTON)
                .add(L10n.t("Hide Blend on Reels"), L10n.t("Remove Meta AI Blend recommendations on reels"), Settings.HIDE_META_AI_BLEND);
        categories.add(metaAi);

        // 13. Glass Tab Bar & Navigation (EVERY TAB BAR SETTING!)
        Category tabBar = new Category(L10n.t("Glass Tab Bar & Navigation"),
                L10n.t("Liquid glass bar, minimize on scroll, blur, hide tabs, header buttons"), SettingsIcons.TAB_BAR)
                .add(L10n.t("iOS Floating Glass Tab Bar"), L10n.t("Enable modern liquid glass bottom bar with backdrop blur"), Settings.GLASS_TAB_BAR)
                .add(L10n.t("Minimize Tab Bar While Scrolling"), L10n.t("Reduces tab bar size while scrolling down, keeping it docked like iOS Instagram"), Settings.GLASS_TAB_BAR_MINIMIZE_ON_SCROLL)
                .add(L10n.t("Hide Tab Bar as You Scroll"), L10n.t("Slide tab bar completely off screen when scrolling down, sliding back when scrolling up"), Settings.GLASS_TAB_BAR_HIDE_ON_SCROLL)
                .add(L10n.t("Tab Bar Background Blur"), L10n.t("Real-time GPU blur behind the floating tab bar (Android 12+)"), Settings.GLASS_TAB_BAR_BLUR)
                .add(L10n.t("Liquid Glass Look"), L10n.t("Rounded capsule pill shape with dedicated bubble highlight"), Settings.GLASS_TAB_BAR_LIQUID)
                .add(L10n.t("Bend Light at Edges"), L10n.t("Liquid glass rim refraction bending background content (Android 13+)"), Settings.GLASS_TAB_BAR_REFRACT)
                .add(L10n.t("Keep Pill Full Size"), L10n.t("Prevent liquid pill from shrinking during scroll"), Settings.GLASS_TAB_BAR_KEEP_SIZE)
                .add(L10n.t("Floating Island Position"), L10n.t("Float the tab bar above the bottom gesture navigation bar"), Settings.GLASS_TAB_BAR_FLOAT)
                .add(L10n.t("Run Reels Behind Tab Bar"), L10n.t("Reels video extends edge-to-edge behind the translucent glass pill"), Settings.GLASS_TAB_BAR_REELS_BEHIND)
                .add(L10n.t("Slide Across Tabs"), L10n.t("Slide your finger sideways across tab bar to preview and switch tabs smoothly"), Settings.GLASS_TAB_BAR_SLIDE)
                .add(L10n.t("Haptic Feedback on Tab Switch"), L10n.t("Gentle subtle haptic tick when switching tabs"), Settings.GLASS_TAB_BAR_HAPTICS)
                .add(L10n.t("iOS Tab Bounce Animation"), L10n.t("Spring bounce effect on tab icons when tapped, matching iOS Instagram feel"), Settings.IOS_TAB_BOUNCE)
                .add(L10n.t("iOS Tab Bar Top Border"), L10n.t("Draws a subtle 0.5dp translucent hairline divider along the top"), Settings.IOS_TAB_HAIRLINE)
                .add(L10n.t("Tap Active Tab to Scroll to Top"), L10n.t("Tapping the currently active tab smoothly scrolls current list to the top"), Settings.IOS_TAB_RECLICK_SCROLL)
                .add(L10n.t("Hide the Home Tab"), L10n.t("Remove Home button from tab bar"), Settings.HIDE_HOME_TAB)
                .add(L10n.t("Hide the Reels Tab"), L10n.t("Remove Reels button from tab bar"), Settings.HIDE_REELS_TAB)
                .add(L10n.t("Show the Reels Tab"), L10n.t("Force Reels button to appear on tab bar"), Settings.SHOW_REELS_TAB)
                .add(L10n.t("Hide the Search Tab"), L10n.t("Remove Explore/Search button from tab bar"), Settings.HIDE_SEARCH_TAB)
                .add(L10n.t("Hide the Create Tab"), L10n.t("Remove Create (+) button from tab bar"), Settings.HIDE_CREATE_TAB)
                .add(L10n.t("Hide the Profile Tab"), L10n.t("Remove Profile button from tab bar"), Settings.HIDE_PROFILE_TAB)
                .add(L10n.t("Show the Messages Tab"), L10n.t("Add direct Messages button to bottom tab bar"), Settings.SHOW_MESSAGES_TAB)
                .add(L10n.t("Hide the Messages Tab"), L10n.t("Remove Messages button from bottom tab bar"), Settings.HIDE_MESSAGES_TAB)
                .add(L10n.t("Show the Activity Tab"), L10n.t("Add Activity (heart) button to bottom tab bar"), Settings.SHOW_ACTIVITY_TAB)
                .add(L10n.t("Hide Create on Home Header"), L10n.t("Remove the plus button from top of Home feed"), Settings.HIDE_HOME_CREATE_BUTTON)
                .add(L10n.t("Hide Notifications on Home Header"), L10n.t("Remove heart notifications icon from top of Home"), Settings.HIDE_HOME_NOTIFICATIONS_BUTTON)
                .add(L10n.t("Search Button on Home Header"), L10n.t("Add search magnifier icon to top of Home"), Settings.SEARCH_BUTTON_ON_HOME)
                .add(L10n.t("Messages Button on Home Header"), L10n.t("Show Messages button at top of Home"), Settings.MESSAGES_BUTTON_ON_HOME)
                .add(L10n.t("Hide Messages on Home Header"), L10n.t("Remove Messages button from top of Home"), Settings.HIDE_HOME_MESSAGES_BUTTON)
                .add(L10n.t("Ghost Mode Button on Home Header"), L10n.t("Add a ghost icon at top of Home to toggle Ghost mode"), Settings.GHOST_BUTTON_ON_HOME)
                .add(L10n.t("Settings Button on Home Header"), L10n.t("Add Instagram Plus gear icon directly on Home top bar"), Settings.SETTINGS_BUTTON_ON_HOME)
                .add(L10n.t("Menu Button on Home Header"), L10n.t("Add a quick popup menu icon on Home header"), Settings.MENU_BUTTON_ON_HOME)
                .add(L10n.t("Center Logo on Home Header"), L10n.t("Align Instagram logo in center of Home header"), Settings.CENTER_HOME_LOGO)
                .add(L10n.t("Center Username on Profiles"), L10n.t("Center the handle in profile header bar"), Settings.CENTER_PROFILE_NAME)
                .add(L10n.t("Center Titles on Other Screens"), L10n.t("Center screen titles across notifications, explore, etc."), Settings.CENTER_SCREEN_TITLES)
                .add(L10n.t("Logo and Titles at Start"), L10n.t("Keep logo and bar titles left-aligned at start"), Settings.TITLES_AT_START);
        categories.add(tabBar);

        // 14. Appearance & System
        Category appearance = new Category(L10n.t("Appearance & System"),
                L10n.t("Pure black AMOLED mode, shortcuts, background battery saving"), SettingsIcons.APPEARANCE)
                .add(L10n.t("Pure Black AMOLED Mode"), L10n.t("True black #000000 background for OLED screens"), Settings.SETTINGS_BLACK_LOOK)
                .add(L10n.t("Settings Shortcut on App Icon"), L10n.t("Add Instagram Plus shortcut to launcher long-press menu"), Settings.SETTINGS_SHORTCUT)
                .add(L10n.t("Open Settings from Logo Long Press"), L10n.t("Press and hold the Instagram logo on Home to open settings"), Settings.SETTINGS_LOGO_PRESS)
                .add(L10n.t("Dismiss Update Warnings"), L10n.t("Disable 'This version of Instagram has expired' dialogs"), Settings.REMOVE_BUILD_EXPIRED_POPUP)
                .add(L10n.t("Stop Background Heartbeat"), L10n.t("Prevent background wakeups to conserve battery"), Settings.STOP_HEARTBEAT)
                .add(L10n.t("Stop Analytics Upload Alarm"), L10n.t("Disable background telemetry alarms"), Settings.STOP_UPLOAD_ALARM);
        categories.add(appearance);

        // 15. Storage & Maintenance
        Category storage = new Category(L10n.t("Storage & Maintenance"),
                L10n.t("Clear media cache, backup and restore settings configuration"), SettingsIcons.STORAGE)
                .add(L10n.t("Date in Backup Filenames"), L10n.t("Include export timestamp in backup filenames"), Settings.DATE_SETTINGS_EXPORT)
                .add(L10n.t("Clear Media Cache"), L10n.t("Wipe cached photos, videos and stories to free up storage"), Settings.CLEAR_MEDIA_CACHE)
                .addAction(L10n.t("Export Settings Backup"), L10n.t("Save all your settings and preferences to a backup file"), this::exportSettings)
                .addAction(L10n.t("Import Settings Backup"), L10n.t("Restore your settings from a previously saved backup file"), this::importSettings);
        categories.add(storage);

        // 16. Support & About
        Category about = new Category(L10n.t("About & Support"),
                L10n.t("Report a problem, support project, versions, licenses"), SettingsIcons.ABOUT)
                .addAction(L10n.t("Report a Problem"), L10n.t("Open GitHub issues with your version info prefilled"), this::openReport)
                .addAction(L10n.t("Support Project"), L10n.t("Support Instagram Plus development on Ko-fi"), this::openSupport)
                .addAction(L10n.t("Open Source Licenses"), L10n.t("GPL-3.0 and third-party notices"), this::openLicenses);
        categories.add(about);

        // Flatten all items for instant live search
        for (Category cat : categories) {
            allItems.addAll(cat.items);
        }
    }

    private void populateOverview() {
        Context context = getContext();

        // Section: HOW YOU USE INSTAGRAM (Categories 0-6)
        overviewContent.addView(createSectionHeader(context, L10n.t("HOW YOU USE INSTAGRAM")));
        for (int i = 0; i <= 6; i++) {
            overviewContent.addView(createCategoryRow(context, categories.get(i)));
        }

        // Section: APP AND MEDIA (Categories 7-11)
        overviewContent.addView(createSectionHeader(context, L10n.t("APP AND MEDIA")));
        for (int i = 7; i <= 11; i++) {
            overviewContent.addView(createCategoryRow(context, categories.get(i)));
        }

        // Section: INTERFACE & NAVIGATION (Categories 12-13)
        overviewContent.addView(createSectionHeader(context, L10n.t("INTERFACE & NAVIGATION")));
        overviewContent.addView(createCategoryRow(context, categories.get(12))); // Glass Tab Bar & Navigation
        overviewContent.addView(createCategoryRow(context, categories.get(13))); // Appearance & System

        // Section: MORE INFO AND SUPPORT (Categories 14-15)
        overviewContent.addView(createSectionHeader(context, L10n.t("MORE INFO AND SUPPORT")));
        overviewContent.addView(createCategoryRow(context, categories.get(14))); // Storage & Maintenance
        overviewContent.addView(createCategoryRow(context, categories.get(15))); // About & Support

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
        dialog.updateBackHandler(); // Immediately register back callback
        overviewScrollY = overviewScroll.getScrollY();

        subpageContent.removeAllViews();
        subpageContent.addView(createSectionHeader(getContext(), category.title));
        for (SettingItem item : category.items) {
            subpageContent.addView(createSettingRow(getContext(), item));
        }

        float offset = 40 * getResources().getDisplayMetrics().density;
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
                })
                .start();
    }

    public boolean closeCategory() {
        if (activeCategory == null) return false;
        activeCategory = null;
        dialog.showTitle(L10n.t("Instagram Plus"), false);
        dialog.updateBackHandler(); // Immediately update back callback state

        float offset = 40 * getResources().getDisplayMetrics().density;
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

    // --- Android 14+ Predictive Back Gesture Preview Support ---

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBackStarted(BackEvent backEvent) {
        if (!canGoBack()) return;
        int width = getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

        // Reveal overview underneath so user sees previous window/page preview!
        overviewScroll.setVisibility(View.VISIBLE);
        overviewScroll.setTranslationX(-width * 0.30f);
        overviewScroll.setAlpha(0.6f);

        View activeView = getActiveBackView();
        if (activeView != null) {
            activeView.animate().cancel();
            activeView.setPivotX(backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT ? 0 : width);
            activeView.setPivotY(activeView.getHeight() / 2f);
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBackProgressed(BackEvent backEvent) {
        if (!canGoBack()) return;
        int width = getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

        float progress = backEvent.getProgress();
        View activeView = getActiveBackView();
        if (activeView != null) {
            float shift = progress * width * 0.85f;
            activeView.setTranslationX(backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT ? shift : -shift);
            float scale = 1.0f - progress * 0.07f;
            activeView.setScaleX(scale);
            activeView.setScaleY(scale);
            activeView.setAlpha(1.0f - progress * 0.20f);
        }

        overviewScroll.setTranslationX(-width * 0.30f * (1.0f - progress));
        overviewScroll.setAlpha(0.6f + 0.4f * progress);
    }

    public void onBackInvoked() {
        int width = getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

        View activeView = getActiveBackView();
        if (activeView != null) {
            activeView.animate().cancel();
            activeView.animate()
                    .translationX(width)
                    .alpha(0f)
                    .setDuration(160)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        activeView.setVisibility(View.GONE);
                        activeView.setTranslationX(0f);
                        activeView.setScaleX(1.0f);
                        activeView.setScaleY(1.0f);
                        activeView.setAlpha(1.0f);
                    })
                    .start();
        }

        overviewScroll.animate().cancel();
        overviewScroll.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    activeCategory = null;
                    if (!currentQuery.isEmpty()) {
                        searchField.setText("");
                    }
                    dialog.showTitle(L10n.t("Instagram Plus"), false);
                    dialog.updateBackHandler();
                })
                .start();
    }

    public void onBackCancelled() {
        View activeView = getActiveBackView();
        if (activeView != null) {
            activeView.animate().cancel();
            activeView.animate()
                    .translationX(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        int width = getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        overviewScroll.animate().cancel();
        overviewScroll.animate()
                .translationX(-width * 0.30f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> overviewScroll.setVisibility(View.GONE))
                .start();
    }

    // --- In-App Edge Swipe Gesture Back Support ---

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!canGoBack()) return super.onInterceptTouchEvent(ev);
        float density = getResources().getDisplayMetrics().density;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = ev.getX();
                touchDownY = ev.getY();
                isSwipingBack = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (touchDownX <= 40 * density) {
                    float dx = ev.getX() - touchDownX;
                    float dy = Math.abs(ev.getY() - touchDownY);
                    if (dx > 20 * density && dx > dy * 1.5f) {
                        isSwipingBack = true;
                        int width = getWidth();
                        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
                        overviewScroll.setVisibility(View.VISIBLE);
                        overviewScroll.setTranslationX(-width * 0.30f);
                        overviewScroll.setAlpha(0.6f);
                        return true;
                    }
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isSwipingBack && !canGoBack()) {
            return super.onTouchEvent(ev);
        }
        int width = getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = ev.getX();
                touchDownY = ev.getY();
                if (touchDownX <= 40 * density) {
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - touchDownX;
                float dy = Math.abs(ev.getY() - touchDownY);
                if (!isSwipingBack && touchDownX <= 40 * density && dx > 20 * density && dx > dy * 1.5f) {
                    isSwipingBack = true;
                    overviewScroll.setVisibility(View.VISIBLE);
                    overviewScroll.setTranslationX(-width * 0.30f);
                    overviewScroll.setAlpha(0.6f);
                }
                if (isSwipingBack) {
                    float shift = Math.max(0, dx);
                    View activeView = getActiveBackView();
                    if (activeView != null) {
                        activeView.setTranslationX(shift);
                        float p = Math.min(1.0f, shift / width);
                        activeView.setAlpha(1.0f - p * 0.20f);
                        overviewScroll.setTranslationX(-width * 0.30f * (1.0f - p));
                        overviewScroll.setAlpha(0.6f + 0.4f * p);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isSwipingBack) {
                    isSwipingBack = false;
                    float finalDx = ev.getX() - touchDownX;
                    if (finalDx > width * 0.30f) {
                        onBackInvoked();
                    } else {
                        onBackCancelled();
                    }
                    return true;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    // --- Action Handlers ---

    private void exportSettings() {
        Activity act = dialog.getActivity();
        if (act != null) {
            Utils.showToastLong(L10n.t("Exporting settings..."));
        }
    }

    private void importSettings() {
        Activity act = dialog.getActivity();
        if (act != null) {
            Utils.showToastLong(L10n.t("Select settings backup file to import"));
        }
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
