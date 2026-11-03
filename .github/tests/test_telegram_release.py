import copy
import hashlib
import os
from pathlib import Path
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/telegram-release.yml').read_text(encoding='utf-8')
source = textwrap.dedent(workflow.split('        run: |\n', 1)[1])
os.environ.setdefault('GITHUB_REPOSITORY', 'wchunlin1006/LocusMimic')
scope = {'__name__': 'workflow_test'}
exec(compile(source, 'telegram-release.yml', 'exec'), scope)


class ReleaseNotificationTest(unittest.TestCase):
    def setUp(self):
        self.release = {
            'tag_name': '20300-2.3.0', 'name': 'LocusMimic 2.3.0',
            'html_url': 'https://github.com/wchunlin1006/LocusMimic/releases/tag/20300-2.3.0',
            'prerelease': False, 'draft': False,
            'body': (ROOT / 'docs/RELEASE_NOTES_2.3.0.md').read_text(encoding='utf-8'),
        }
        self.originals = {key: scope[key] for key in ('telegram', 'github_release', 'download', 'TOKEN', 'CHAT_ID')}
        self.env = os.environ.copy()

    def tearDown(self):
        scope.update(self.originals)
        os.environ.clear()
        os.environ.update(self.env)

    def test_real_notes_are_formatted_summary_with_full_link(self):
        caption = scope['release_caption'](self.release)
        self.assertIn('<b>', caption)
        self.assertNotIn('**', caption)
        self.assertNotIn('## ', caption)
        self.assertIn(self.release['html_url'], caption)
        self.assertLessEqual(scope['displayed_length'](caption), 1024)
        for heading in ('新增', '优化', '修复', '升级提醒'):
            self.assertIn('<b>' + heading + '</b>', caption)

    def test_markup_is_safe(self):
        self.release['body'] = '<script>bad</script> & **bold**'
        self.assertIn('&lt;script&gt;', scope['release_caption'](self.release))
        self.release['body'] = '![](https://example.com/image.png)'
        self.assertNotIn('<img', scope['release_caption'](self.release))

    def test_oversize_entries_omitted_without_breaking_formatting(self):
        self.release['body'] = '## Changes\n- **' + 'a' * 32769 + '**\n- Short entry'
        caption = scope['release_caption'](self.release)
        self.assertIn('Short entry', caption)
        self.assertNotIn('a' * 100, caption)
        self.assertLessEqual(scope['displayed_length'](caption), 1024)

    def test_title_over_budget_is_rejected(self):
        self.release['name'] = 'x' * 1100
        with self.assertRaises(RuntimeError):
            scope['release_caption'](self.release)

    def test_emoji_and_html_entities_count_visible_units(self):
        self.assertEqual(scope['displayed_length']('<b>&lt;&amp;&#128512;</b>'), 4)

    def test_unexpected_url_rejected(self):
        self.release['html_url'] = 'https://example.com/release'
        with self.assertRaises(RuntimeError):
            scope['release_caption'](self.release)

    def test_rc_is_labelled(self):
        self.release['prerelease'] = True
        self.assertIn('RC 预发布', scope['release_caption'](self.release))

    def run_main(self, dry=False, multiple=False, corrupt=False):
        apk = b'test-apk'
        files = {'app.apk': apk, 'SHA256SUMS.txt': (hashlib.sha256(apk).hexdigest() + '  app.apk\n').encode()}
        if multiple:
            files['other.apk'] = apk
            files['SHA256SUMS.txt'] += (hashlib.sha256(apk).hexdigest() + '  other.apk\n').encode()
        if corrupt:
            files['app.apk'] = b'corrupt!'
        release = copy.deepcopy(self.release)
        release['assets'] = [{'name': n, 'size': len(d), 'state': 'uploaded'} for n, d in files.items()]
        release['assets'].append({'name': 'notes.md', 'size': 100, 'state': 'uploaded'})
        scope['github_release'] = lambda tag: release
        scope['download'] = lambda asset: files[asset['name']]
        scope['TOKEN'], scope['CHAT_ID'] = 'test-token', '@test-channel'
        os.environ.update(RELEASE_TAG=release['tag_name'], DRY_RUN=str(dry).lower())
        calls = []

        def telegram(method, payload=None, **kwargs):
            if method == 'getMe':
                return {'id': 1}
            if method == 'getChat':
                return {'type': 'channel'}
            if method == 'getChatMember':
                return {'status': 'administrator', 'can_post_messages': True}
            calls.append((method, kwargs))
            return {'message_id': 1, 'document': {'file_name': 'app.apk'}, 'caption': 'summary'}

        scope['telegram'] = telegram
        scope['main']()
        return calls

    def test_one_message_one_apk_and_no_checksum_attachment(self):
        calls = self.run_main()
        self.assertEqual(len(calls), 1)
        method, request = calls[0]
        self.assertEqual(method, 'sendDocument')
        body = request['body']
        self.assertIn(b'filename="app.apk"', body)
        self.assertNotIn(b'filename="SHA256SUMS.txt"', body)
        self.assertNotIn(b'filename="notes.md"', body)
        self.assertIn(b'name="document"', body)
        self.assertIn(b'name="parse_mode"\r\n\r\nHTML', body)
        self.assertNotIn(b'rich_message', body)
        caption = body.split(b'name="caption"\r\n\r\n')[1].split(b'\r\n--')[0].decode()
        self.assertLessEqual(scope['displayed_length'](caption), 1024)

    def test_dry_run_posts_nothing(self):
        self.assertEqual(self.run_main(dry=True), [])

    def test_multiple_apks_rejected(self):
        with self.assertRaises(RuntimeError):
            self.run_main(multiple=True)

    def test_checksum_mismatch_rejected(self):
        with self.assertRaises(RuntimeError):
            self.run_main(corrupt=True)


if __name__ == '__main__':
    unittest.main()
