# Optional TMDB title lookup

In **Settings > Player UI > Title lookup > TMDB API Key**, enter your **API Key**
from [your TMDB API settings](https://www.themoviedb.org/settings/api), then enable
**Look up titles with TMDB**. This feature is off by default. Enter the
32-character hexadecimal API Key, not the API Read Access Token or your account
password. API keys use TMDB's `api_key` authentication. Saving a nonempty key
automatically checks it with TMDB's authentication endpoint, without starting
playback or enabling title lookup. The field shows whether TMDB verified or
rejected the key, or whether verification could not complete. Connection/service
failures are not reported as invalid keys. Save again to retry. Verification
results survive app restarts and reset when the key is saved again or removed.
The saved result is bound to that credential and reflects its last check, not a
guarantee that the key can never expire or be revoked. The verification record is
also excluded from backups and support exports.
If an older build saved a key without a verification record, opening these
settings automatically verifies it once; the key does not need to be re-entered.
Existing read access tokens saved by an earlier test
build remain usable internally, but the settings field now accepts API keys only.

The player displays its locally resolved title immediately. In the background,
TMDB receives the cleaned title, any known year, and the season/episode numbers.
Stream URLs, file paths, and playback positions are not sent.

For episodes, a unique matching show and explicit season/episode numbers are
required. A successful lookup can replace an incorrect embedded episode title.
Absolute episode numbers without a season are not assumed to belong to season 1.
For movies, a known year is required. The year can come from a matching release
filename (for example, `Movie.Name.2025.1080p.mkv`) even when the displayed title
has no year. The filename title must match the displayed movie name; unrelated
filenames cannot supply a year. This does not add a year to the displayed title.
Ambiguous results, missing metadata,
authentication errors, and network failures leave the local title unchanged.
Results are cached in memory for the current player session.

Android logs under `mpv-tmdb` report lookup starts, matches, skips, and failures.
They include cleaned lookup titles and numeric metadata, but not credentials,
request URLs, raw filenames, or response bodies.

Filename parsing cannot determine whether a release uses the same episode order
as TMDB. Even a matching show and episode number can describe different episodes
when releases use alternate ordering. Disable lookup in that case.

Lookup changes only the displayed title and episode title. It does not change the
media file, playback identity, resume positions, or external-player results.

Tokens are stored in Android's app-private no-backup directory, outside the
preferences and config files included in app backups and support exports. The
token field is masked. Clear the field and save to remove the token.

## Attribution

Title metadata is provided by [The Movie Database](https://www.themoviedb.org).
This product uses the TMDB API but is not endorsed or certified by TMDB.

The unmodified TMDB logo in the About screen is the existing TMDB brand asset
also used by NuvioTV (`rating_tmdb.png`). TMDB's
[attribution requirements](https://www.themoviedb.org/about/logos-attribution)
apply to that asset and the API data.
