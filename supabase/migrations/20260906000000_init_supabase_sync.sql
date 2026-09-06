-- ============================================================================
-- BUTTERFLY SUPABASE CLOUD SYNC SCHEMA & ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================
-- Run this script in the Supabase SQL Editor for your project.
-- All tables are user-isolated using auth.uid().
-- Large video/media files are NEVER stored in PostgreSQL; only metadata & URLs.

-- 1. USER PROFILES
CREATE TABLE IF NOT EXISTS public.user_profiles (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL DEFAULT '',
    handle text NOT NULL DEFAULT '',
    bio text DEFAULT '',
    avatar_url text DEFAULT NULL,
    avatar_preset text DEFAULT NULL,
    updated_at timestamptz DEFAULT now()
);
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own profile" ON public.user_profiles;
CREATE POLICY "Users can manage own profile" ON public.user_profiles
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 2. WATCH HISTORY
CREATE TABLE IF NOT EXISTS public.watch_history (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id text NOT NULL,
    title text NOT NULL,
    channel_name text NOT NULL DEFAULT '',
    thumbnail_url text,
    duration text DEFAULT '',
    progress_fraction real DEFAULT 0,
    provider_id text DEFAULT 'youtube',
    timestamp bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);
CREATE INDEX IF NOT EXISTS idx_watch_history_user_time ON public.watch_history(user_id, timestamp DESC);
ALTER TABLE public.watch_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own watch history" ON public.watch_history;
CREATE POLICY "Users can manage own watch history" ON public.watch_history
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 3. WATCH LATER (BOOKMARKS)
CREATE TABLE IF NOT EXISTS public.watch_later (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id text NOT NULL,
    title text NOT NULL,
    channel_name text NOT NULL DEFAULT '',
    thumbnail_url text,
    duration text DEFAULT '',
    provider_id text DEFAULT 'youtube',
    timestamp bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);
CREATE INDEX IF NOT EXISTS idx_watch_later_user_time ON public.watch_later(user_id, timestamp DESC);
ALTER TABLE public.watch_later ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own watch later" ON public.watch_later;
CREATE POLICY "Users can manage own watch later" ON public.watch_later
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 4. LIKED VIDEOS
CREATE TABLE IF NOT EXISTS public.liked_videos (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id text NOT NULL,
    title text NOT NULL,
    channel_name text NOT NULL DEFAULT '',
    thumbnail_url text,
    duration text DEFAULT '',
    provider_id text DEFAULT 'youtube',
    timestamp bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);
CREATE INDEX IF NOT EXISTS idx_liked_videos_user_time ON public.liked_videos(user_id, timestamp DESC);
ALTER TABLE public.liked_videos ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own liked videos" ON public.liked_videos;
CREATE POLICY "Users can manage own liked videos" ON public.liked_videos
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 5. USER PLAYLISTS
CREATE TABLE IF NOT EXISTS public.user_playlists (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    playlist_id text NOT NULL,
    title text NOT NULL,
    videos_json jsonb DEFAULT '[]'::jsonb,
    created_at bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, playlist_id)
);
ALTER TABLE public.user_playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own playlists" ON public.user_playlists;
CREATE POLICY "Users can manage own playlists" ON public.user_playlists
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 6. SEARCH HISTORY
CREATE TABLE IF NOT EXISTS public.search_history (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    query text NOT NULL,
    timestamp bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, query)
);
ALTER TABLE public.search_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own search history" ON public.search_history;
CREATE POLICY "Users can manage own search history" ON public.search_history
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 7. BUNKR ALBUMS
CREATE TABLE IF NOT EXISTS public.bunkr_albums (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    album_id text NOT NULL,
    title text NOT NULL,
    source_url text NOT NULL,
    is_enabled boolean NOT NULL DEFAULT true,
    last_scan_time bigint NOT NULL DEFAULT 0,
    item_count int NOT NULL DEFAULT 0,
    created_at bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, album_id)
);
ALTER TABLE public.bunkr_albums ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own bunkr albums" ON public.bunkr_albums;
CREATE POLICY "Users can manage own bunkr albums" ON public.bunkr_albums
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 8. BUNKR FILES
CREATE TABLE IF NOT EXISTS public.bunkr_files (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    file_id text NOT NULL,
    album_id text NOT NULL,
    title text NOT NULL,
    source_url text NOT NULL,
    thumbnail_url text,
    media_type text NOT NULL DEFAULT 'video',
    duration text DEFAULT '',
    resolution text DEFAULT '',
    file_size text DEFAULT '',
    order_index int NOT NULL DEFAULT 0,
    last_updated bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, file_id)
);
CREATE INDEX IF NOT EXISTS idx_bunkr_files_album ON public.bunkr_files(user_id, album_id);
ALTER TABLE public.bunkr_files ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own bunkr files" ON public.bunkr_files;
CREATE POLICY "Users can manage own bunkr files" ON public.bunkr_files
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 9. CLOUD SOCIAL SOURCES
CREATE TABLE IF NOT EXISTS public.cloud_social_sources (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    source_id text NOT NULL,
    type text NOT NULL,
    name text NOT NULL,
    source_url text NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    last_sync_timestamp bigint NOT NULL DEFAULT 0,
    item_count int NOT NULL DEFAULT 0,
    new_item_count int NOT NULL DEFAULT 0,
    extra_config_json text DEFAULT '',
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, source_id)
);
ALTER TABLE public.cloud_social_sources ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own cloud social sources" ON public.cloud_social_sources;
CREATE POLICY "Users can manage own cloud social sources" ON public.cloud_social_sources
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 10. CLOUD SOCIAL MEDIA
CREATE TABLE IF NOT EXISTS public.cloud_social_media (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    media_id text NOT NULL,
    source_id text NOT NULL,
    type text NOT NULL,
    remote_id text NOT NULL,
    parent_id text,
    title text NOT NULL,
    caption text,
    source_url text NOT NULL,
    direct_stream_url text,
    thumbnail_url text,
    mime_type text DEFAULT 'video/mp4',
    file_size bigint DEFAULT 0,
    formatted_size text DEFAULT '',
    duration_ms bigint DEFAULT 0,
    media_category text DEFAULT 'video',
    date_timestamp bigint NOT NULL,
    resolution text DEFAULT 'HD',
    headers_json text DEFAULT '{}',
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, media_id)
);
CREATE INDEX IF NOT EXISTS idx_cloud_social_media_source ON public.cloud_social_media(user_id, source_id);
ALTER TABLE public.cloud_social_media ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own cloud social media" ON public.cloud_social_media;
CREATE POLICY "Users can manage own cloud social media" ON public.cloud_social_media
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 11. APP PREFERENCES
CREATE TABLE IF NOT EXISTS public.app_preferences (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    preferences_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz DEFAULT now()
);
ALTER TABLE public.app_preferences ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own app preferences" ON public.app_preferences;
CREATE POLICY "Users can manage own app preferences" ON public.app_preferences
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 12. OFFLINE DOWNLOADS METADATA (Metadata and URLs only)
CREATE TABLE IF NOT EXISTS public.offline_downloads_metadata (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id text NOT NULL,
    title text NOT NULL,
    channel_name text NOT NULL DEFAULT '',
    thumbnail_url text,
    quality_label text DEFAULT 'Auto',
    total_bytes bigint DEFAULT 0,
    status text DEFAULT 'COMPLETED',
    timestamp bigint NOT NULL,
    updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);
ALTER TABLE public.offline_downloads_metadata ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own downloads metadata" ON public.offline_downloads_metadata;
CREATE POLICY "Users can manage own downloads metadata" ON public.offline_downloads_metadata
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 13. USER BEHAVIOR SIGNALS (Recommendation Intelligence Event Stream)
CREATE TABLE IF NOT EXISTS public.user_behavior_signals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    video_id text NOT NULL,
    event_type text NOT NULL, -- 'watched', 'completed', 'liked', 'disliked', 'skipped', 'dwell'
    watch_time_ms bigint DEFAULT 0,
    progress_fraction real DEFAULT 0,
    category text DEFAULT 'general',
    channel_name text DEFAULT '',
    provider_id text DEFAULT 'youtube',
    hour_of_day int DEFAULT 0,
    metadata jsonb DEFAULT '{}'::jsonb,
    created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_behavior_signals_user_time ON public.user_behavior_signals(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_behavior_signals_video ON public.user_behavior_signals(user_id, video_id);
ALTER TABLE public.user_behavior_signals ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own behavior signals" ON public.user_behavior_signals;
CREATE POLICY "Users can manage own behavior signals" ON public.user_behavior_signals
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 14. USER PREFERENCE PROFILE (Aggregated Intelligence Profile)
CREATE TABLE IF NOT EXISTS public.user_preference_profile (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    disliked_videos jsonb DEFAULT '[]'::jsonb,
    disliked_channels jsonb DEFAULT '[]'::jsonb,
    disliked_keywords jsonb DEFAULT '[]'::jsonb,
    disliked_categories jsonb DEFAULT '{}'::jsonb,
    favorite_channels jsonb DEFAULT '{}'::jsonb,
    hourly_categories jsonb DEFAULT '{}'::jsonb,
    hourly_channels jsonb DEFAULT '{}'::jsonb,
    high_completion_channels jsonb DEFAULT '{}'::jsonb,
    early_bounce_channels jsonb DEFAULT '{}'::jsonb,
    taste_vector_json jsonb DEFAULT '{}'::jsonb,
    updated_at timestamptz DEFAULT now()
);
ALTER TABLE public.user_preference_profile ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can manage own preference profile" ON public.user_preference_profile;
CREATE POLICY "Users can manage own preference profile" ON public.user_preference_profile
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
