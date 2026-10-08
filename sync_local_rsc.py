with open(r'c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager\app\src\main\java\com\example\utils\UniversalSetupHelper.kt', 'r', encoding='utf-8') as f:
    kt_code = f.read()

start_mark = 'val template = """'
end_mark = '"""\n        return template'
s_idx = kt_code.find(start_mark) + len(start_mark)
e_idx = kt_code.find(end_mark)
template_body = kt_code[s_idx:e_idx]

rsc_body = template_body.replace('__SAFE_SSID__', 'Kyaw_Gyi')
rsc_body = rsc_body.replace('__SAFE_PASS__', 'Khant1234@')
rsc_body = rsc_body.replace('__SAFE_CAP__', '250')
rsc_body = rsc_body.replace('@DOL@', '$')

paths = [
    r'c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager\setup.rsc',
    r'c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager\universal_setup.rsc',
    r'c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager\mikrotikConfiguration\core\universal_setup.rsc',
    r'c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager\mkcaptivePortal\setup.rsc'
]

for p in paths:
    with open(p, 'w', encoding='utf-8') as f:
        f.write(rsc_body)
    print('Updated RSC file:', p)
