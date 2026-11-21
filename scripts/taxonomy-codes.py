"""
Generates shared/src/commonMain/kotlin/dorai/shared/roi/TaxonomyCodeLists.kt from the EBA DORA taxonomy.

Inputs (all local, nothing is fetched):
  argv[1]  folder with the DORA module files: <taxonomy>/.../fws/dora/<release>/ (holds mod/ and tab/)
  argv[2]  member labels as JSON {"eba_ZZ:x791": "Low", ...}, read from the dictionary's dom/*/mem-lab-en.xml
  argv[3]  RoiSpec options as TSV lines "column<TAB>type<TAB>key=label|key=label"

Each closed-list option is matched to the taxonomy member with the closest English label; OVERRIDES fixes
the options whose CIR wording differs from the taxonomy's. The script stops if any option stays unmatched.
"""
import json
import glob
import re
import sys

dora, labels_file, ours_file = sys.argv[1:4]
labels = json.load(open(labels_file))

OVERRIDES = {
    # Type of entity: the taxonomy words these differently from the CIR (keys follow RoiSpec ENTITY_TYPES).
    'ENTITY': {'1': 'x12', '2': 'x300', '3': 'x301', '4': 'x302', '5': 'x599', '6': 'x303', '7': 'x310', '8': 'x304',
               '9': 'x643', '10': 'x305', '11': 'x306', '12': 'x307', '13': 'x639', '14': 'x308', '15': 'x309',
               '16': 'x320', '17': 'x311', '18': 'x312', '19': 'x313', '20': 'x314', '21': 'x315', '22': 'x316',
               '23': 'x317', '24': 'x318'},
    'B_01.02.0050': {'1': 'x53', '2': 'x551', '3': 'x56', '4': 'x21', '5': 'x210'},
    'B_07.01.0060': {'3': 'x965'},
}

# B_99.01 has one column per defined option; its dimension names the closed list the option belongs to.
DEFINED = {'KTQ': 'B_02.01.0020', 'DSS': 'B_02.02.0170', 'IFD': 'B_06.01.0100',
           'SAA': 'B_07.01.0050', 'KTW': 'B_07.01.0090', 'ISD': 'B_07.01.0100'}


def clean(text):
    text = text.replace('\ufffd', "'").replace('\u2013', '-').replace('\u2014', '-').strip().strip('"').strip()
    return re.sub(r'\s+', ' ', text)


def norm(s):
    s = s.lower().replace('renewd', 'renewed').replace('identfier', 'identifier')
    s = re.sub(r'[^a-z0-9 ]', ' ', s)
    return set(w for w in s.split() if w not in {'the', 'of', 'a', 'an', 'and', 'or', 'in', 'to', 'for', 'by'})


def score(a, b):
    a, b = norm(a), norm(b)
    return len(a & b) / max(1, len(a | b))


ours = {}
for line in open(ours_file, encoding='utf-8'):
    code, typ, opts = (line.rstrip('\n').split('\t') + [''])[:3]
    ours[code] = (typ, [o.split('=', 1) for o in opts.split('|') if o])

codes = {}
primary = {}
prefixes = {}
activities = []
problems = []
for f in sorted(glob.glob(f'{dora}/tab/*/*.json')):
    for tn, tt in json.load(open(f))['tableTemplates'].items():
        template = tn.replace('-', '.')
        fields = tt.get('tc:keys', {}).get('primary', {}).get('fields', [])
        if fields:
            primary[template] = [f'{template}.{c[1:]}' for c in fields]
        for c, v in tt['columns'].items():
            allowed = v.get('eba:documentation', {}).get('AllowedValue')
            if not allowed:
                continue
            column = f'{template}.{c[1:]}'
            members = list(allowed)
            domain = members[0].split(':')[0]
            if column == 'B_06.01.0020':
                activities = sorted((m.split(':')[1], clean(labels.get(m, m))) for m in members)
                codes[column] = {k: f'{domain}:{k}' for k, _ in activities}
                continue
            if column not in ours:
                problems.append(f'{column}: in the taxonomy, not in RoiSpec')
                continue
            typ, options = ours[column]
            if typ in ('Country', 'Currency', 'CodeType'):
                prefixes[column] = domain
                continue
            override = OVERRIDES.get('ENTITY' if column in ('B_01.01.0040', 'B_01.02.0040') else column, {})
            mapping = {}
            for key, label in options:
                if key in override:
                    mapping[key] = f'{domain}:{override[key]}'
                    continue
                best = max(members, key=lambda m: score(label, labels.get(m, '')))
                if score(label, labels.get(best, '')) < 0.3:
                    problems.append(f'{column} {key}={label!r}: no member matches (best {best}={labels.get(best)!r})')
                mapping[key] = best
            if len(set(mapping.values())) != len(mapping):
                problems.append(f'{column}: two options share a member: {mapping}')
            codes[column] = mapping

definitions = {}
for tn, tt in json.load(open(f'{dora}/tab/b_99.01/b_99.01.json'))['tableTemplates'].items():
    for c, v in tt['columns'].items():
        (dim, member), = [(k.split(':')[1], m) for k, m in v['dimensions'].items() if k != 'concept']
        source = DEFINED.get(dim)
        keys = [k for k, m in codes.get(source, {}).items() if m == member]
        if len(keys) != 1:
            problems.append(f'B_99.01.{c[1:]}: no single option of {source} is {member}')
            continue
        definitions[f'{source}|{keys[0]}'] = c

if problems:
    sys.exit('\n'.join(problems))


def kstr(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$') + '"'


out = ['package dorai.shared.roi', '',
       '// Generated by scripts/taxonomy-codes.py from the EBA DORA taxonomy (reporting framework 4.0, errata 5).',
       '// Do not edit by hand: change the script or its overrides and run it again.', '',
       '/** Member codes of the EBA taxonomy for each closed-list column, by RoiSpec option key. */',
       'internal object TaxonomyCodeLists {',
       '    /** Column code to (option key to member QName, for example "eba_ZZ:x791"). */',
       '    val members: Map<String, Map<String, String>> = mapOf(']
for column in sorted(codes):
    pairs = ', '.join(f'{kstr(k)} to {kstr(v)}' for k, v in codes[column].items())
    out.append(f'        {kstr(column)} to mapOf({pairs}),')
out += ['    )', '',
        '    /** Columns whose members are the value itself under a domain prefix (countries, currencies, code types). */',
        '    val domains: Map<String, String> = mapOf(']
for column in sorted(prefixes):
    out.append(f'        {kstr(column)} to {kstr(prefixes[column])},')
out += ['    )', '',
        '    /** The primary key of each template in the taxonomy: rows may not share these values. */',
        '    val primaryKeys: Map<String, List<String>> = mapOf(']
for template in sorted(primary):
    out.append(f'        {kstr(template)} to listOf(' + ', '.join(kstr(c) for c in primary[template]) + '),')
out += ['    )', '',
        '    /** B_99.01: the column that holds the definition of each option, keyed "column|option key". */',
        '    val definitionColumns: Map<String, String> = mapOf(']
for key in sorted(definitions, key=lambda k: definitions[k]):
    out.append(f'        {kstr(key)} to {kstr(definitions[key])},')
out += ['    )', '',
        '    /** B_06.01.0020: the licensed activities the taxonomy lists, keyed by member code. */',
        '    val licensedActivities: List<RoiOption> = listOf(']
for key, label in activities:
    out.append(f'        RoiOption({kstr(key)}, {kstr(label)}),')
out += ['    )', '}', '']
sys.stdout.reconfigure(encoding='utf-8', newline='\n')
print('\n'.join(out), end='')
