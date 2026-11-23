from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output' / 'pdf' / 'calculus-pre-lecture-summary.pdf'
OUT.parent.mkdir(parents=True, exist_ok=True)
for name, fn in [('Arial','arial.ttf'),('Arial-Bold','arialbd.ttf'),('Arial-Italic','ariali.ttf'),('Arial-BoldItalic','arialbi.ttf')]:
    pdfmetrics.registerFont(TTFont(name, str(Path('C:/Windows/Fonts') / fn)))
pdfmetrics.registerFontFamily('Arial', normal='Arial', bold='Arial-Bold', italic='Arial-Italic', boldItalic='Arial-BoldItalic')

INK = colors.HexColor('#182535')
TEAL = colors.HexColor('#146776')
styles = {
    'title': ParagraphStyle('title', fontName='Arial-Bold', fontSize=20, leading=24, textColor=INK, spaceAfter=5),
    'subtitle': ParagraphStyle('subtitle', fontName='Arial', fontSize=9, leading=12, textColor=colors.HexColor('#58616D'), spaceAfter=12),
    'head': ParagraphStyle('head', fontName='Arial-Bold', fontSize=12, leading=15, textColor=TEAL, spaceBefore=8, spaceAfter=5),
    'body': ParagraphStyle('body', fontName='Arial', fontSize=10, leading=13.1, textColor=INK, spaceAfter=4),
    'formula': ParagraphStyle('formula', fontName='Arial', fontSize=10, leading=13.3, textColor=INK, spaceAfter=4, leftIndent=8),
    'cell': ParagraphStyle('cell', fontName='Arial', fontSize=9, leading=12, textColor=INK, alignment=TA_CENTER),
}
story=[]
def p(s, kind='body'):
    story.append(Paragraph(s, styles[kind]))

p('Calculus Pre-lecture Task 1','title')
p('Key notes • §1.3 Trigonometric Functions • §1.5 Exponential Functions • §1.6, printed pp. 59-62','subtitle')

p('1.3  Trigonometric Functions','head')
p('<b>Angles.</b> π radians = 180°; degrees × π/180 = radians; radians × 180/π = degrees. Arc length: <b>s = rθ</b> (θ in radians). Standard position: vertex at the origin, initial ray on the positive x-axis. Counterclockwise angles are positive; clockwise angles are negative. Use radians unless degrees are stated.')
p('<b>Trig ratios.</b> For a point P(x, y) on a circle of radius r: sin θ = y/r, cos θ = x/r, tan θ = y/x. In a right triangle these are opposite/hypotenuse, adjacent/hypotenuse, and opposite/adjacent. On the unit circle, P = (cos θ, sin θ).')
p('tan θ = sin θ / cos θ;  cot θ = cos θ / sin θ;  sec θ = 1 / cos θ;  csc θ = 1 / sin θ.','formula')

rows = [
    ['θ', '0', 'π/6 = 30°', 'π/4 = 45°', 'π/3 = 60°', 'π/2 = 90°'],
    ['sin θ', '0', '1/2', '√2/2', '√3/2', '1'],
    ['cos θ', '1', '√3/2', '√2/2', '1/2', '0'],
    ['tan θ', '0', '√3/3', '1', '√3', 'undefined'],
]
table=Table([[Paragraph(c, styles['cell']) for c in row] for row in rows], colWidths=[63,53,95,95,95,106], hAlign='LEFT')
table.setStyle(TableStyle([
    ('BACKGROUND',(0,0),(-1,0),colors.HexColor('#EAF3F5')),
    ('LINEBELOW',(0,0),(-1,0),0.5,colors.HexColor('#8AAEB4')),
    ('LINEBELOW',(0,-1),(-1,-1),0.5,colors.HexColor('#8AAEB4')),
    ('TOPPADDING',(0,0),(-1,-1),2),('BOTTOMPADDING',(0,0),(-1,-1),2),
]))
story.extend([table, Spacer(1,5)])
p('<b>Signs and graphs.</b> ASTC: in quadrants I-IV, respectively, all, sine, tangent, cosine are positive (including their reciprocals). Sine/cosine have range [−1, 1]; secant/cosecant have |y| ≥ 1; tangent/cotangent have all real outputs. Periods: 2π for sin, cos, sec, csc; π for tan, cot. Cos/sec are even; the others are odd. Tan/sec are undefined at θ = π/2 + kπ; cot/csc at θ = kπ (k an integer).')
p('<b>Main identities</b> (where defined):','body')
p('sin<super>2</super> θ + cos<super>2</super> θ = 1;  1 + tan<super>2</super> θ = sec<super>2</super> θ;  1 + cot<super>2</super> θ = csc<super>2</super> θ.<br/>sin(A ± B) = sin A cos B ± cos A sin B.<br/>cos(A + B) = cos A cos B − sin A sin B;  cos(A − B) = cos A cos B + sin A sin B.<br/>sin 2θ = 2 sin θ cos θ;  cos 2θ = cos<super>2</super> θ − sin<super>2</super> θ.<br/>sin<super>2</super> θ = (1 − cos 2θ)/2;  cos<super>2</super> θ = (1 + cos 2θ)/2.','formula')
p('<b>Useful results.</b> Law of cosines: c<super>2</super> = a<super>2</super> + b<super>2</super> − 2ab cos θ, where θ is opposite c. In radians: |sin θ| ≤ |θ| and |1 − cos θ| ≤ |θ|. For y = A sin(b(x − C)) + D (or cosine), amplitude = |A|, period = 2π/|b| (b ≠ 0), horizontal shift = C, midline = D.')

p('1.5  Exponential Functions','head')
p('<b>Definition.</b> f(x) = a<super>x</super>, where a &gt; 0 and a ≠ 1: the variable is in the exponent. Domain: all real numbers; range: (0, ∞). The graph passes through (0, 1), with horizontal asymptote y = 0. It increases if a &gt; 1 and decreases if 0 &lt; a &lt; 1.')
p('<b>Exponent rules</b> (a, b &gt; 0): a<super>0</super> = 1; a<super>−x</super> = 1/a<super>x</super>; a<super>p/q</super> = <super>q</super>√(a<super>p</super>); a<super>x</super>a<super>y</super> = a<super>x+y</super>; a<super>x</super>/a<super>y</super> = a<super>x−y</super>; (a<super>x</super>)<super>y</super> = a<super>xy</super>; (ab)<super>x</super> = a<super>x</super>b<super>x</super>; (a/b)<super>x</super> = a<super>x</super>/b<super>x</super>.')
p('<b>Natural exponential.</b> e ≈ 2.71828; y = e<super>x</super> has tangent slope 1 at (0, 1). For y<sub>0</sub> &gt; 0, y = y<sub>0</sub>e<super>kt</super> models growth when k &gt; 0 and decay when k &lt; 0. Annual compounding: A = P(1 + r)<super>t</super>; continuous compounding: A = Pe<super>rt</super>. P is the initial amount; r is a decimal annual rate and t is in years.')

p('1.6  Inverse Functions and Logarithms | pp. 59-62 only','head')
p('<b>Inverses.</b> An inverse reverses a one-to-one function: solve y = f(x) for x, then swap x and y. Graphs reflect across y = x; domain and range swap. Check f<super>−1</super>(f(x)) = x and f(f<super>−1</super>(x)) = x on the relevant domains. A domain restriction may be needed: x<super>2</super> for x ≥ 0 has inverse √x.')
p('<b>Logarithms.</b> y = log<sub>a</sub> x means a<super>y</super> = x (a &gt; 0, a ≠ 1). Domain: x &gt; 0; range: all real numbers. ln x = log<sub>e</sub> x; log x = log<sub>10</sub> x. In particular, ln e = 1 and ln 1 = 0.')
p('ln(uv) = ln u + ln v;  ln(u/v) = ln u − ln v;  ln(1/u) = −ln u;<br/>ln(u<super>r</super>) = r ln u  (u, v &gt; 0).  Inverse rules: e<super>ln x</super> = x (x &gt; 0),  ln(e<super>x</super>) = x.<br/>Change of base: log<sub>a</sub> x = ln x / ln a.  Exponential conversion: a<super>x</super> = e<super>x ln a</super>.','formula')
p('<b>Solving for time.</b> Take logarithms to bring down an exponent: A = Pa<super>t</super> gives t = ln(A/P)/ln a. For y = y<sub>0</sub>e<super>−kt</super>, k &gt; 0, half-life = ln 2/k, independent of y<sub>0</sub>. Periodic trig functions need restricted domains to have inverses (introduced at the end of p. 62).')

def footer(c, doc):
    c.setStrokeColor(colors.HexColor('#D6E1E5'))
    c.line(44, 32, A4[0]-44, 32)
    c.setFont('Arial',7)
    c.setFillColor(colors.HexColor('#67737F'))
    c.drawString(44,21,'Source: University Calculus: Early Transcendentals, 4th ed. in SI Units, §§1.3, 1.5; §1.6 pp. 59-62.')

doc=SimpleDocTemplate(str(OUT), pagesize=A4, rightMargin=44,leftMargin=44,topMargin=32,bottomMargin=43,
    title='Calculus Pre-lecture Task 1: Sections 1.3, 1.5 and 1.6',author='')
doc.build(story,onFirstPage=footer,onLaterPages=footer)
r=PdfReader(OUT)
print('Output:', OUT)
print('Pages:', len(r.pages))
assert len(r.pages)==1, 'Summary must be exactly one page'
