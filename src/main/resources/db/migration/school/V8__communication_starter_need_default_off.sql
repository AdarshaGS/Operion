-- Communication and Messaging are off by default for every organisation (AppModule.defaultEnabled),
-- so the Starter Pack no longer pre-ticks the "announcements and messages" need - a school opts in.
UPDATE school_starter_needs SET default_yes = 0 WHERE need_key = 'COMMUNICATION';
