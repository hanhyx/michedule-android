-- Timeline feature tables
CREATE TABLE IF NOT EXISTS public.timelines (
    id TEXT PRIMARY KEY,
    room_code TEXT NOT NULL,
    date TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    created_by TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.timeline_places (
    id TEXT PRIMARY KEY,
    timeline_id TEXT NOT NULL REFERENCES timelines(id) ON DELETE CASCADE,
    place_name TEXT NOT NULL,
    time TEXT NOT NULL DEFAULT '',
    memo TEXT NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.timeline_photos (
    id TEXT PRIMARY KEY,
    place_id TEXT NOT NULL,
    timeline_id TEXT NOT NULL REFERENCES timelines(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.timeline_stickers (
    id TEXT PRIMARY KEY,
    timeline_id TEXT NOT NULL REFERENCES timelines(id) ON DELETE CASCADE,
    place_id TEXT,
    photo_id TEXT,
    sticker_type TEXT NOT NULL DEFAULT 'emoji',
    sticker_value TEXT NOT NULL,
    pos_x REAL NOT NULL,
    pos_y REAL NOT NULL,
    scale REAL NOT NULL DEFAULT 1.0,
    rotation REAL NOT NULL DEFAULT 0.0,
    placed_by TEXT NOT NULL
);

-- RLS
ALTER TABLE public.timelines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.timeline_places ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.timeline_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.timeline_stickers ENABLE ROW LEVEL SECURITY;

-- Policies (allow all for anon - same pattern as existing tables)
DO $$ BEGIN
IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'timelines' AND policyname = 'Allow all for anon') THEN
    CREATE POLICY "Allow all for anon" ON public.timelines FOR ALL USING (true) WITH CHECK (true);
END IF;
IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'timeline_places' AND policyname = 'Allow all for anon') THEN
    CREATE POLICY "Allow all for anon" ON public.timeline_places FOR ALL USING (true) WITH CHECK (true);
END IF;
IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'timeline_photos' AND policyname = 'Allow all for anon') THEN
    CREATE POLICY "Allow all for anon" ON public.timeline_photos FOR ALL USING (true) WITH CHECK (true);
END IF;
IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'timeline_stickers' AND policyname = 'Allow all for anon') THEN
    CREATE POLICY "Allow all for anon" ON public.timeline_stickers FOR ALL USING (true) WITH CHECK (true);
END IF;
END $$;

-- Storage bucket for timeline photos
INSERT INTO storage.buckets (id, name, public)
VALUES ('timeline-photos', 'timeline-photos', true)
ON CONFLICT (id) DO NOTHING;
