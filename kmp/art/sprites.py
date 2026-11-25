import math
def shade(h, f):
    h=h.lstrip('#'); r,g,b=[int(h[i:i+2],16) for i in (0,2,4)]
    if f<1: r,g,b=[int(c*f) for c in (r,g,b)]
    else: r,g,b=[int(c+(255-c)*(f-1)) for c in (r,g,b)]
    return '#%02x%02x%02x'%(r,g,b)

KINGDOMS = {'verde':'#69bd4f','vermelho':'#e0594f','azul':'#4c82dd','ciano':'#3fc2c0','amarelo':'#f1c443','roxo':'#9b6ddf','laranja':'#ef8a3d'}
NEUTRAL = '#b3ab9c'
WATER = '#2b6a99'
SKIN='#f1c7a1'; SKIN_D='#d9a77f'

def shadow(rx=20):
    return f'<ellipse cx="50" cy="90" rx="{rx}" ry="{rx*0.26:.1f}" fill="#000" opacity=".22"/>'

def pips(n):
    s=''; w=7; x0=50-(n-1)*w/2
    for i in range(n):
        x=x0+i*w
        s+=f'<path d="M{x} 93.5 L{x+2.8} 96.5 L{x} 99.5 L{x-2.8} 96.5Z" fill="#f2c34a" stroke="#7a5a12" stroke-width=".8"/>'
    return s

def body(tunic, legs='#4a3b2e', boots='#3a2c22'):
    t=tunic; td=shade(t,.78)
    return (f'<rect x="42" y="72" width="7" height="14" rx="2" fill="{legs}"/><rect x="51" y="72" width="7" height="14" rx="2" fill="{shade(legs,.8)}"/>'
            f'<rect x="41" y="84" width="8.5" height="5" rx="2" fill="{boots}"/><rect x="50.5" y="84" width="8.5" height="5" rx="2" fill="{boots}"/>'
            f'<path d="M38 52 Q50 46 62 52 L64 76 Q50 80 36 76Z" fill="{t}"/><path d="M50 48.5 Q57 49 62 52 L64 76 Q57 78.5 50 79Z" fill="{td}"/>'
            f'<rect x="38" y="66" width="24" height="4" fill="{shade(t,.6)}"/>')

def head(y=40):
    return f'<circle cx="50" cy="{y}" r="9.5" fill="{SKIN}"/><path d="M50 {y-9.5} A9.5 9.5 0 0 1 50 {y+9.5}Z" fill="{SKIN_D}"/><circle cx="46.5" cy="{y+1}" r="1.2" fill="#3a2a20"/><circle cx="53" cy="{y+1}" r="1.2" fill="#3a2a20"/>'

def peasant():
    return (shadow(17)+
        '<line x1="67" y1="34" x2="67" y2="89" stroke="#8a6338" stroke-width="3" stroke-linecap="round"/>'
        '<path d="M61 26 V36 M67 24 V36 M73 26 V36 M61 36 H73" stroke="#9aa1a8" stroke-width="2.2" fill="none" stroke-linecap="round"/>'
        + body('#9c7a4c') + head(42) +
        '<ellipse cx="50" cy="35" rx="16" ry="4.5" fill="#e3bf62"/><path d="M41 35 Q42 24 50 23 Q58 24 59 35Z" fill="#edcf7c"/><path d="M50 23 Q58 24 59 35 H50Z" fill="#cfa84c"/><rect x="41" y="32" width="18" height="2.4" fill="#b5473a"/>'
        '<circle cx="64" cy="60" r="4" fill="'+SKIN+'"/>' + pips(1))

def spearman():
    return (shadow(17)+
        '<line x1="68" y1="14" x2="68" y2="89" stroke="#7a5532" stroke-width="3" stroke-linecap="round"/>'
        '<path d="M68 5 L72 16 L68 19 L64 16Z" fill="#cfd6dc"/><path d="M68 5 L72 16 L68 19Z" fill="#a3acb4"/>'
        '<path d="M64.5 20 L71.5 20 L70 26 L66 26Z" fill="#3f8f8a"/>'
        + body('#4f8c88') + head(42) +
        '<path d="M38 39 Q39 28 50 27 Q61 28 62 39 Z" fill="#b9c1c8"/><path d="M50 27 Q61 28 62 39 H50Z" fill="#949da5"/><ellipse cx="50" cy="39" rx="15" ry="3" fill="#a7b0b8"/>'
        '<circle cx="65" cy="60" r="4" fill="'+SKIN+'"/>' + pips(2))

def baron():
    return (shadow(19)+
        '<path d="M37 50 Q50 44 63 50 L68 86 Q50 90 32 86Z" fill="#5e1f28"/>'
        + body('#a3343f', legs='#3b3340') + head(40) +
        '<path d="M39 38 Q40 26 50 25 Q60 26 61 38 Z" fill="#c4ccd3"/><path d="M50 25 Q60 26 61 38 H50Z" fill="#9aa3ab"/><rect x="39" y="36" width="22" height="3" rx="1" fill="#8f979f"/>'
        '<path d="M50 25 Q47 13 56 9 Q53 17 57 22 Q54 24 50 25Z" fill="#e2453d"/>'
        '<line x1="70" y1="30" x2="70" y2="58" stroke="#dfe5ea" stroke-width="3.2" stroke-linecap="round"/><line x1="70" y1="30" x2="70" y2="58" stroke="#aeb7bf" stroke-width="1.2"/><rect x="64.5" y="57" width="11" height="3" rx="1.2" fill="#c9a227"/><rect x="68.6" y="60" width="2.8" height="6" fill="#6b4a2a"/>'
        '<circle cx="34" cy="63" r="10" fill="#c9a227"/><circle cx="34" cy="63" r="7.5" fill="#8a2a33"/><circle cx="34" cy="63" r="2.4" fill="#e9c75a"/>' + pips(3))

def knight():
    a='#c3ccd5'; ad=shade(a,.78)
    return (shadow(21)+
        f'<rect x="41" y="72" width="8" height="15" rx="2" fill="{a}"/><rect x="51" y="72" width="8" height="15" rx="2" fill="{ad}"/>'
        f'<path d="M36 50 Q50 43 64 50 L66 76 Q50 81 34 76Z" fill="{a}"/><path d="M50 46.5 Q58 47 64 50 L66 76 Q58 79 50 79.5Z" fill="{ad}"/>'
        '<rect x="36" y="64" width="30" height="4" fill="#d9a62e"/>'
        f'<rect x="40" y="25" width="20" height="23" rx="6" fill="{a}"/><path d="M50 25 H54 Q60 25 60 31 V42 Q60 48 54 48 H50Z" fill="{ad}"/>'
        '<rect x="42" y="34" width="16" height="2.6" rx="1" fill="#2a2f36"/><rect x="49" y="25" width="2" height="23" fill="#d9a62e"/>'
        '<path d="M50 25 Q44 11 55 6 Q52 14 59 18 Q56 23 50 25Z" fill="#f0c03e"/>'
        '<line x1="72" y1="20" x2="72" y2="60" stroke="#e8edf1" stroke-width="3.8" stroke-linecap="round"/><line x1="72" y1="20" x2="72" y2="60" stroke="#aeb7bf" stroke-width="1.3"/><rect x="65" y="59" width="14" height="3.4" rx="1.4" fill="#d9a62e"/><rect x="70.6" y="62" width="2.8" height="7" fill="#6b4a2a"/>'
        '<path d="M24 52 H44 V66 Q44 78 34 82 Q24 78 24 66Z" fill="#2f4f8f"/><path d="M34 52 H44 V66 Q44 78 34 82Z" fill="#253f73"/><path d="M24 52 H44 V66 Q44 78 34 82 Q24 78 24 66Z" fill="none" stroke="#d9a62e" stroke-width="2"/><path d="M34 56 V76 M28 64 H40" stroke="#f0c03e" stroke-width="2.6" stroke-linecap="round"/>'
        + pips(4))

def castle(flag='#69bd4f'):
    s='#ddd5c4'; sd='#b6ab95'; dk='#5a4632'
    return (shadow(36)+
        f'<rect x="28" y="46" width="44" height="42" fill="{s}"/><rect x="58" y="46" width="14" height="42" fill="{sd}"/>'
        f'<path d="M28 46 h6 v-5 h6 v5 h6 v-5 h6 v5 h6 v-5 h6 v5 h6 v-5 h2 v5" fill="{s}"/>'
        f'<rect x="14" y="40" width="18" height="48" fill="{s}"/><rect x="26" y="40" width="6" height="48" fill="{sd}"/>'
        f'<rect x="68" y="40" width="18" height="48" fill="{s}"/><rect x="79" y="40" width="7" height="48" fill="{sd}"/>'
        '<path d="M12 41 L23 22 L34 41Z" fill="#c4583e"/><path d="M23 22 L34 41 H23Z" fill="#9e4330"/>'
        '<path d="M66 41 L77 22 L88 41Z" fill="#c4583e"/><path d="M77 22 L88 41 H77Z" fill="#9e4330"/>'
        f'<rect x="40" y="26" width="20" height="22" fill="{s}"/><rect x="52" y="26" width="8" height="22" fill="{sd}"/>'
        '<path d="M37 27 L50 10 L63 27Z" fill="#c4583e"/><path d="M50 10 L63 27 H50Z" fill="#9e4330"/>'
        f'<line x1="50" y1="11" x2="50" y2="0" stroke="#5a4632" stroke-width="1.8"/><path d="M50.9 0.5 L67 4.5 L50.9 9Z" fill="{flag}"/><path d="M50.9 5 L67 4.5 L50.9 9Z" fill="{shade(flag,.8)}"/>'
        f'<path d="M43 88 V77 A7 7 0 0 1 57 77 V88Z" fill="{dk}"/><rect x="20" y="52" width="5" height="8" rx="2.5" fill="{dk}"/><rect x="74" y="52" width="5" height="8" rx="2.5" fill="{dk}"/><rect x="47.5" y="33" width="5" height="8" rx="2.5" fill="{dk}"/>')

def tower():
    s='#d2c8b4'; sd='#a99d86'
    return (shadow(20)+
        f'<rect x="36" y="38" width="28" height="50" fill="{s}"/><rect x="54" y="38" width="10" height="50" fill="{sd}"/>'
        f'<rect x="32" y="28" width="36" height="12" fill="{s}"/><rect x="56" y="28" width="12" height="12" fill="{sd}"/>'
        f'<path d="M32 28 V21 H39 V28 M43.5 28 V21 H50.5 V28 M55 28 V21 H62 V28" fill="{s}" stroke="none"/><rect x="61" y="21" width="7" height="7" fill="{sd}"/>'
        '<rect x="32" y="21" width="7" height="7" fill="'+s+'"/><rect x="43.5" y="21" width="7" height="7" fill="'+s+'"/><rect x="55" y="21" width="6" height="7" fill="'+s+'"/>'
        '<rect x="47" y="48" width="4" height="11" rx="2" fill="#4a3a2a"/><path d="M44 88 V79 A6 6 0 0 1 56 79 V88Z" fill="#6a4c30"/>'
        '<path d="M36 64 H64 M36 76 H54" stroke="#b9ae98" stroke-width="1.2"/>')

def strong_tower(flag='#69bd4f'):
    s='#9ea8b3'; sd='#7a848f'; t='#b4bdc6'
    return (shadow(27)+
        f'<rect x="28" y="34" width="44" height="54" fill="{s}"/><rect x="58" y="34" width="14" height="54" fill="{sd}"/>'
        f'<rect x="24" y="22" width="52" height="14" fill="{t}"/><rect x="62" y="22" width="14" height="14" fill="{sd}"/>'
        + ''.join(f'<rect x="{x}" y="14" width="7" height="8" fill="{t if x<62 else sd}"/>' for x in (24,35,46.5,58,69)) +
        f'<rect x="40" y="2" width="20" height="18" fill="{t}"/><rect x="52" y="2" width="8" height="18" fill="{sd}"/>'
        f'<path d="M44 40 H56 V62 L50 57 L44 62Z" fill="{flag}"/><path d="M50 40 H56 V62 L50 57Z" fill="{shade(flag,.8)}"/>'
        '<path d="M43 88 V77 A7 7 0 0 1 57 77 V88Z" fill="#3c3f45"/><rect x="47.5" y="7" width="5" height="8" rx="2.5" fill="#2f3237"/>'
        '<path d="M28 70 H72 M28 52 H44 M56 52 H72" stroke="#8b949e" stroke-width="1.2"/>')

def house(x, y, w=26, wall='#efe2c0', roof='#b9563b'):
    h=w*0.62
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{wall}"/><rect x="{x+w*0.62}" y="{y}" width="{w*0.38}" height="{h}" fill="{shade(wall,.85)}"/>'
            f'<path d="M{x-3} {y+1} L{x+w/2} {y-w*0.5} L{x+w+3} {y+1}Z" fill="{roof}"/><path d="M{x+w/2} {y-w*0.5} L{x+w+3} {y+1} H{x+w/2}Z" fill="{shade(roof,.8)}"/>'
            f'<rect x="{x+w*0.18}" y="{y+h*0.4}" width="{w*0.22}" height="{h*0.6}" fill="#6a4c30"/><rect x="{x+w*0.5}" y="{y+h*0.25}" width="{w*0.2}" height="{w*0.2}" fill="#5a7fa8"/>')

def field(cx, cy, rx, ry, rows=4):
    s=f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="#a77a45"/>'
    for i in range(rows):
        yy=cy-ry+ (i+0.7)*(2*ry/rows); half=rx*math.sqrt(max(0,1-((yy-cy)/ry)**2))*0.9
        s+=f'<line x1="{cx-half:.1f}" y1="{yy:.1f}" x2="{cx+half:.1f}" y2="{yy:.1f}" stroke="#ecc54e" stroke-width="3" stroke-linecap="round"/>'
    return s

def farm(v=1):
    s=shadow(30)
    if v==1:
        s+=field(58,82,26,8)+house(26,62)
    elif v==2:
        s+=field(40,82,24,8)+house(48,60,28,'#eadcbc','#8f6a3e')+'<path d="M18 80 Q18 64 28 64 Q38 64 38 80Z" fill="#e3b84a"/><path d="M28 64 Q38 64 38 80 H28Z" fill="#c49a34"/>'
    else:
        s+=field(62,84,22,7)+('<path d="M30 88 L34 52 H46 L50 88Z" fill="#ece0c4"/><path d="M40 52 H46 L50 88 H40Z" fill="#cdbf9f"/><path d="M32 53 L40 42 L48 53Z" fill="#b9563b"/>'
            '<g transform="rotate(20 40 50)"><rect x="38.5" y="22" width="3" height="56" fill="#8a6338"/><rect x="12" y="48.5" width="56" height="3" fill="#8a6338"/>'
            '<rect x="41.5" y="24" width="7" height="22" fill="#f4ede0" opacity=".9"/><rect x="31.5" y="54" width="7" height="22" fill="#f4ede0" opacity=".9"/><rect x="14" y="41.5" width="22" height="7" fill="#f4ede0" opacity=".9"/><rect x="44" y="51.5" width="22" height="7" fill="#f4ede0" opacity=".9"/></g>'
            '<circle cx="40" cy="50" r="3" fill="#5a4632"/>')
    return s

def pine():
    d='#2e7a48'; l='#47a262'
    s=shadow(18)+'<rect x="46" y="74" width="8" height="15" fill="#7a4a2a"/><rect x="50" y="74" width="4" height="15" fill="#5e3820"/>'
    for top,base,half in ((40,78,30),(24,58,24),(8,40,18)):
        s+=f'<path d="M50 {top} L{50+half} {base} L{50-half} {base}Z" fill="{d}"/><path d="M50 {top} L50 {base} L{50-half} {base}Z" fill="{l}"/>'
    return s

def palm():
    s=shadow(16)+'<path d="M53 90 Q58 62 46 36" stroke="#a5743f" stroke-width="7" fill="none" stroke-linecap="round"/><path d="M53 90 Q58 62 46 36" stroke="#84592d" stroke-width="7" fill="none" stroke-dasharray="2 5"/>'
    for a,c in ((-160,'#3e9c58'),(-115,'#56b86c'),(-65,'#3e9c58'),(-20,'#56b86c'),(25,'#3e9c58'),(-200,'#56b86c')):
        s+=f'<path d="M46 34 Q60 26 74 34 Q60 32 46 36Z" fill="{c}" transform="rotate({a+20} 46 35)"/>'
    s+='<circle cx="47" cy="39" r="3.2" fill="#6b4423"/><circle cx="42" cy="38" r="3.2" fill="#5a381c"/>'
    return s

def grave():
    return (shadow(17)+'<ellipse cx="50" cy="86" rx="18" ry="5.5" fill="#7d6346"/>'
        '<path d="M39 86 V62 A11 11 0 0 1 61 62 V86Z" fill="#bdbab2"/><path d="M52 86 V51.2 A11 11 0 0 1 61 62 V86Z" fill="#9c988f"/>'
        '<path d="M50 61 V77 M44 66.5 H56" stroke="#7f7b73" stroke-width="2.6" stroke-linecap="round"/>'
        '<path d="M33 87 l2 -6 l2 6 M63 87 l2 -5 l2 5 M58 88 l1.5 -4 l1.5 4" stroke="#5f9a49" stroke-width="1.6" fill="none" stroke-linecap="round"/>')

SPRITES = {'peasant':peasant,'spearman':spearman,'baron':baron,'knight':knight,'castle':castle,'tower':tower,'strong_tower':strong_tower,'farm1':lambda:farm(1),'farm2':lambda:farm(2),'farm3':lambda:farm(3),'pine':pine,'palm':palm,'grave':grave}

def svg(inner, size=120, label=''):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 100 100" role="img" aria-label="{label}">{inner}</svg>'

R3=math.sqrt(3)
def hex_tile(cx, cy, r, color, t=None, stroke=True):
    t = r*0.22 if t is None else t
    h=R3/2*r
    top=[(cx+r,cy),(cx+r/2,cy+h),(cx-r/2,cy+h),(cx-r,cy),(cx-r/2,cy-h),(cx+r/2,cy-h)]
    side=[(cx+r,cy),(cx+r/2,cy+h),(cx-r/2,cy+h),(cx-r,cy),(cx-r,cy+t),(cx-r/2,cy+h+t),(cx+r/2,cy+h+t),(cx+r,cy+t)]
    P=lambda pts:' '.join(f'{x:.1f},{y:.1f}' for x,y in pts)
    hi=[(cx-r/2,cy-h),(cx+r/2,cy-h)]
    s=f'<polygon points="{P(side)}" fill="{shade(color,.62)}"/>'
    s+=f'<polygon points="{P(top)}" fill="{color}"'+(f' stroke="{shade(color,.8)}" stroke-width="{r*0.04:.2f}"' if stroke else '')+'/>'
    s+=f'<polyline points="{P([(cx-r,cy),(cx-r/2,cy-h),(cx+r/2,cy-h)])}" fill="none" stroke="{shade(color,1.35)}" stroke-width="{r*0.06:.2f}" stroke-linejoin="round" opacity=".8"/>'
    return s
