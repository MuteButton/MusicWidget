"""Check widget resource variants before Android silently falls back to a base file."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'

def verify(root):
    main = root / 'app/src/main'
    manifest = ET.parse(main / 'AndroidManifest.xml').getroot()
    providers = [m.get(ANDROID + 'resource') for m in manifest.findall('./application/receiver/meta-data')
                 if m.get(ANDROID + 'name') == 'android.appwidget.provider']
    assert len(providers) == 1, 'Expected exactly one widget provider resource'
    reference = providers[0]
    assert reference.startswith('@xml/'), 'Widget metadata must reference XML'
    expected = reference.split('/')[-1] + '.xml'
    variants = []
    for folder in (main / 'res').glob('xml*'):
        for path in folder.glob('*.xml'):
            xml = ET.parse(path).getroot()
            if xml.tag == 'appwidget-provider':
                assert path.name == expected, f'Orphan widget override: {path.relative_to(root)}; manifest uses {expected}'
                variants.append(folder.name)
                layout = xml.get(ANDROID + 'initialLayout')
                assert (main / 'res/layout' / (layout.split('/')[-1] + '.xml')).exists(), f'Missing initial layout: {layout}'
    assert set(variants) == {'xml', 'xml-v31'}, f'Missing or unexpected widget variants: {variants}'
    modern = ET.parse(main / 'res/xml-v31' / expected).getroot()
    assert (modern.get(ANDROID+'targetCellWidth'), modern.get(ANDROID+'targetCellHeight')) == ('4', '1'), 'Modern widget must default to 4 by 1'
    app = manifest.find('application')
    for attribute in ('icon', 'roundIcon'):
        resource = app.get(ANDROID+attribute)
        kind, name = resource[1:].split('/')
        assert any((main / 'res' / folder / (name+'.xml')).exists() for folder in (kind, kind+'-anydpi')), f'No version-independent {attribute}: {resource}'
    print('PASS: widget variants match manifest; 4-by-1 override and baseline launcher icons exist.')

if __name__ == '__main__':
    verify(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1])
