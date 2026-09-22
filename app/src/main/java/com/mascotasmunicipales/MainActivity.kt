package com.mascotasmunicipales

import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val teal = Color.rgb(20,127,149)
    private val tealDark = Color.rgb(15,100,117)
    private val bg = Color.rgb(241,246,248)
    private val dark = Color.rgb(32,49,58)
    private val gray = Color.rgb(105,120,128)
    private val white = Color.WHITE

    data class Pet(val name:String,val species:String,val breed:String,val sex:String,val age:String,val color:String,val territory:String,val status:String,val qr:String,val image:Int)
    data class Report(val pet:String,val type:String,val status:String,val date:String)

    private val pets = mutableListOf(
        Pet("Luna","Perro","Criolla","Hembra","3 años","Café","La Esperanza","Con responsable","ZPQ-7F3A9",R.drawable.luna),
        Pet("Michi","Gato","Doméstico pelo corto","Macho","2 años","Gris atigrado","Centro","Con responsable","ZPQ-B12C4",R.drawable.michi),
        Pet("Rocco","Perro","Mestizo","Macho","5 años","Negro y café","Algarra III","En adopción","ZPQ-9D8E1",R.drawable.rocco),
        Pet("Nina","Perro","Criolla pequeña","Hembra","8 meses","Crema","San Antonio","En custodia","ZPQ-4A6F2",R.drawable.nina)
    )
    private val reports = mutableListOf(
        Report("Toby","Pérdida","Abierto","15/09/2026"),
        Report("Luna","Encontrado","Cerrado","15/07/2026")
    )

    private var currentTab = 0
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showApp() }

    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun lp(w:Int=-1,h:Int=-2)=LinearLayout.LayoutParams(if(w<0) w else dp(w),if(h<0) h else dp(h))
    private fun tv(text:String,size:Float=14f,bold:Boolean=false,color:Int=dark):TextView=TextView(this).apply{this.text=text;textSize=size;setTextColor(color);typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT;setPadding(dp(2),dp(2),dp(2),dp(2))}
    private fun bgShape(color:Int=white,radius:Int=16,stroke:Int=0,strokeColor:Int=Color.TRANSPARENT)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();if(stroke>0)setStroke(dp(stroke),strokeColor)}
    private fun card():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=bgShape();setPadding(dp(14),dp(12),dp(14),dp(12));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(5),dp(6),dp(5),dp(6))}}
    private fun button(label:String,action:()->Unit)=Button(this).apply{text=label;textSize=13f;setTextColor(white);setBackgroundColor(teal);typeface=Typeface.DEFAULT_BOLD;setOnClickListener{action()};layoutParams=lp(-1,50).apply{setMargins(dp(5),dp(7),dp(5),dp(7))}}
    private fun root():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
    private fun scroll(c:View):ScrollView=ScrollView(this).apply{addView(c);setFillViewport(true)}

    private fun showApp(){
        val r=root()
        val frame=FrameLayout(this); content=frame
        r.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setBackgroundColor(white);setPadding(dp(4),dp(4),dp(4),dp(4))}
        val labels=listOf("Inicio","Reportes","Mascotas","Territorio","Perfil")
        labels.forEachIndexed{idx,label-> val b=TextView(this).apply{text="${if(idx==0)"⌂" else if(idx==1)"▣" else if(idx==2)"♥" else if(idx==3)"⌖" else "⚙"}\n$label";gravity=Gravity.CENTER;textSize=11f;setTextColor(if(idx==0)teal else gray);setPadding(0,dp(5),0,dp(5));setOnClickListener{currentTab=idx;renderTab()};layoutParams=LinearLayout.LayoutParams(0,dp(60),1f)};nav.addView(b)}
        r.addView(nav)
        setContentView(r);renderTab()
    }

    private fun header(title:String,subtitle:String?=null):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(teal);setPadding(dp(20),dp(18),dp(20),dp(18));addView(tv(title,21f,true,white));subtitle?.let{addView(tv(it,12f,false,Color.rgb(220,240,245)))}}

    private fun renderTab(){
        for (index in 0 until nav.childCount) {
            (nav.getChildAt(index) as? TextView)?.setTextColor(if (index == currentTab) teal else gray)
        }
        when(currentTab){0->home();1->reportList();2->pets();3->territory();4->profile()}
    }

    private fun replace(v:View){content.removeAllViews();content.addView(v,FrameLayout.LayoutParams(-1,-1));}

    private fun home(){
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        c.addView(header("🐾 Mascotas Municipales","Alcaldía de Zipaquirá · DIVIPOLA 25899"))
        c.addView(tv("Modo campo activo · los cambios se sincronizan al recuperar conexión.",12f,false,white).apply{setBackgroundColor(tealDark);setPadding(dp(20),dp(10),dp(20),dp(10))})
        c.addView(tv("Panorama del municipio",15f,true).apply{setPadding(dp(18),dp(18),dp(18),dp(7))})
        val grid=GridLayout(this).apply{columnCount=2;setPadding(dp(12),0,dp(12),0)}
        val indicators=listOf("8.412" to "Mascotas registradas","71%" to "Vacunación antirrábica","137" to "Reportes activos","289" to "Adopciones 2026")
        indicators.forEach{(n,l)-> val box=card();box.addView(tv(n,23f,true,teal));box.addView(tv(l,13f,true));box.addView(tv("Información municipal",11f,false,gray));val gl=GridLayout.LayoutParams();gl.width=0;gl.height=dp(110);gl.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);gl.setMargins(dp(4),dp(4),dp(4),dp(4));grid.addView(box,gl)}
        c.addView(grid)
        c.addView(tv("Accesos rápidos",15f,true).apply{setPadding(dp(18),dp(14),dp(18),dp(5))})
        val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(12),0,dp(12),0)}
        listOf("🔎\nBuscar por QR" to {qrScreen()},"💉\nJornadas" to {healthScreen()},"♥\nAdopta" to {pets()}).forEach{(s,a)->quick.addView(TextView(this).apply{text=s;gravity=Gravity.CENTER;textSize=12f;setTextColor(teal);background=bgShape();setPadding(dp(8),dp(14),dp(8),dp(14));setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(0,dp(85),1f).apply{setMargins(dp(4),0,dp(4),0)}})}
        c.addView(quick)
        c.addView(tv("Registros recientes",15f,true).apply{setPadding(dp(18),dp(16),dp(18),dp(5))})
        pets.take(3).forEach{p->c.addView(petCard(p){detail(p)})}
        replace(scroll(c))
    }

    private fun petCard(p:Pet,action:()->Unit)=card().apply{
        orientation=LinearLayout.HORIZONTAL;setOnClickListener{action()}
        val im=ImageView(this@MainActivity).apply{setImageResource(p.image);scaleType=ImageView.ScaleType.CENTER_CROP};addView(im,LinearLayout.LayoutParams(dp(72),dp(72)))
        val col=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)}
        col.addView(tv(p.name,16f,true));col.addView(tv("${p.especie()} · ${p.breed}",12f,false,gray));col.addView(tv("📍 ${p.territory}",11f,false,gray));col.addView(tv(p.status,11f,true,if(p.status=="En adopción")Color.rgb(190,130,0) else Color.rgb(25,150,100)));addView(col,LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun Pet.especie()=species

    private fun pets(){
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        c.addView(header("Directorio de mascotas","Búsqueda por nombre, código o territorio"))
        val search=EditText(this).apply{hint="Buscar (ej. Luna o ZPQ-7F3A9)";background=bgShape();setPadding(dp(14),0,dp(14),0);layoutParams=lp(-1,52).apply{setMargins(dp(15),dp(12),dp(15),dp(5))}}
        c.addView(search)
        val count=tv("${pets.size} mascotas registradas",13f,true).apply{setPadding(dp(18),dp(8),dp(18),dp(5))};c.addView(count)
        fun render(list:List<Pet>){ while(c.childCount>3)c.removeViewAt(3);list.forEach{p->c.addView(petCard(p){detail(p)})} }
        search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,cnt:Int){val q=s.toString().lowercase();render(pets.filter{it.name.lowercase().contains(q)||it.qr.lowercase().contains(q)||it.territory.lowercase().contains(q)})};override fun afterTextChanged(s:android.text.Editable?){} })
        render(pets);replace(scroll(c))
    }

    private fun detail(p:Pet){
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        c.addView(header("Detalle Mascota","Alcaldía de Zipaquirá"))
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))}
        val im=ImageView(this).apply{setImageResource(p.image);scaleType=ImageView.ScaleType.CENTER_CROP};body.addView(im,LinearLayout.LayoutParams(-1,dp(180)).apply{setMargins(0,0,0,dp(12))})
        body.addView(tv(p.name,25f,true));body.addView(tv("${p.species} · ${p.breed}",14f,false,gray));body.addView(tv("${p.status} · IDENTIFICADA",12f,true,teal).apply{setPadding(0,dp(8),0,dp(10))})
        val d=card();d.addView(tv("Ficha pública",15f,true));d.addView(tv("Sexo: ${p.sex}\nEdad: ${p.age}\nColor: ${p.color}\nTerritorio: ${p.territory}\nCódigo QR: ${p.qr}",13f));body.addView(d)
        val q=card();q.gravity=Gravity.CENTER; q.addView(ImageView(this).apply{setImageResource(R.drawable.qr_luna);layoutParams=LinearLayout.LayoutParams(dp(150),dp(150))});q.addView(tv("Código público · ${p.qr}",12f,true,teal));body.addView(q)
        body.addView(card().apply{addView(tv("🔒 Datos del responsable protegidos",13f,true));addView(tv("El QR no expone teléfono, dirección, documento ni correo.",11f,false,gray))})
        body.addView(button("CONTACTAR ORGANIZACIÓN DE BIENESTAR"){Toast.makeText(this@MainActivity,"Solicitud de contacto enviada",Toast.LENGTH_SHORT).show()})
        body.addView(button("← VOLVER"){pets()})
        c.addView(scroll(body));replace(c)
    }

    private fun reportList(){
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(header("Mis Reportes","Alertas de bienestar animal"));c.addView(tv("TUS REPORTES (R04)",14f,true).apply{setPadding(dp(18),dp(18),dp(18),dp(5))})
        reports.forEach{r->c.addView(card().apply{addView(tv("${r.pet} · ${r.type}",15f,true));addView(tv("${r.status} · ${r.date}",12f,false,gray))})}
        c.addView(button("+ NUEVO REPORTE"){newReport()});replace(scroll(c))
    }

    private fun newReport(){
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(header("Nuevo Reporte","Pasos cortos y campos claros · funciona sin conexión"));val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(15),dp(18),dp(15))}
        val photo=TextView(this).apply{text="📷\nAgregar foto (opcional)";gravity=Gravity.CENTER;background=bgShape();setPadding(0,dp(25),0,dp(25));textSize=14f;setTextColor(teal)};b.addView(photo)
        val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Pérdida","Encontrado","Avistamiento"))};b.addView(tv("Tipo de reporte",14f,true));b.addView(type,lp(-1,55))
        val species=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Perro","Gato"))};b.addView(tv("Especie",14f,true));b.addView(species,lp(-1,55))
        val comuna=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Comuna 1 · Centro Histórico","Comuna 2 · Nororiental","Comuna 3 · Suroriental","Comuna 4 · Occidental"))};b.addView(tv("Ubicación (comuna)",14f,true));b.addView(comuna,lp(-1,55))
        val desc=EditText(this).apply{hint="Describe lo que observaste...";minLines=4;gravity=Gravity.TOP;background=bgShape();setPadding(dp(12),dp(10),dp(12),dp(10))};b.addView(tv("Descripción",14f,true));b.addView(desc)
        b.addView(tv("No incluyas datos personales de terceros. La ubicación se guarda a nivel de territorio.",11f,false,gray).apply{setPadding(0,dp(10),0,dp(4))})
        b.addView(button("ENVIAR REPORTE"){reports.add(Report("Mascota sin identificar",type.selectedItem.toString(),"Abierto","15/09/2026"));Toast.makeText(this@MainActivity,"Reporte guardado localmente · pendiente de sincronización",Toast.LENGTH_LONG).show();reportList()})
        c.addView(scroll(b));replace(c)
    }

    private fun qrScreen(){
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(header("Información Básica","Consulta pública mediante QR"));val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))}
        val q=card();q.gravity=Gravity.CENTER;q.addView(ImageView(this@MainActivity).apply{setImageResource(R.drawable.qr_luna);layoutParams=LinearLayout.LayoutParams(dp(190),dp(190))});q.addView(tv("Código QR-R04-0001",13f,true,teal));b.addView(q)
        b.addView(card().apply{addView(tv("LUNA · ACTIVO / IDENTIFICADO",18f,true));addView(tv("Perro · Criolla\nTerritorio: La Esperanza\nVacuna: Vigente",13f,false,gray))})
        b.addView(card().apply{addView(tv("🔒 Datos del responsable protegidos",13f,true));addView(tv("Solo se muestra la información pública mínima necesaria.",11f,false,gray))})
        b.addView(button("LA ENCONTRÉ"){reports.add(Report("Luna","Encontrado","Abierto","15/09/2026"));Toast.makeText(this@MainActivity,"Reporte de hallazgo creado",Toast.LENGTH_SHORT).show()});b.addView(button("← VOLVER"){home()});c.addView(scroll(b));replace(c)
    }

    private fun territory(){val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(header("Territorio","Catastro municipal de mascotas"));val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))};b.addView(card().apply{addView(tv("Zipaquirá",20f,true));addView(tv("4 comunas · 52 barrios · 2 corregimientos · 11 veredas",13f,false,gray));addView(tv("197 km² de extensión municipal",12f,false,gray))});listOf("Comuna 1 · Centro Histórico — 2.418 mascotas","Comuna 2 · Nororiental — 2.106 mascotas","Comuna 3 · Suroriental — 1.987 mascotas","Comuna 4 · Occidental — 1.901 mascotas").forEach{b.addView(card().apply{addView(tv(it,14f,true));addView(tv("Información territorial para filtros y seguimiento.",11f,false,gray))})};c.addView(scroll(b));replace(c)}

    private fun healthScreen(){val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(header("Jornadas","Salud y bienestar animal"));val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))};b.addView(card().apply{addView(tv("Vacunación antirrábica",17f,true));addView(tv("Cobertura actual: 71% sobre mascotas registradas",13f,false,gray))});b.addView(card().apply{addView(tv("Esterilización",17f,true));addView(tv("Registrar procedimientos y asociarlos a cada mascota.",13f,false,gray))});b.addView(button("REGISTRAR EVENTO SANITARIO"){Toast.makeText(this@MainActivity,"Evento sanitario guardado",Toast.LENGTH_SHORT).show()});c.addView(scroll(b));replace(c)}

    private fun profile(){val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};c.addView(header("Perfil y ajustes","Cuenta, accesibilidad y roles del sistema"));val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))};b.addView(card().apply{addView(tv("María Fernanda R.",16f,true));addView(tv("Funcionaria · Bienestar Animal · Verificada",13f,false,gray))});b.addView(tv("Accesibilidad",15f,true).apply{setPadding(0,dp(15),0,dp(5))});val large=Switch(this).apply{text="Texto más grande"};b.addView(large);val contrast=Switch(this).apply{text="Alto contraste"};b.addView(contrast);b.addView(tv("Roles del sistema",15f,true).apply{setPadding(0,dp(15),0,dp(5))});listOf("Ciudadano — registrar, reportar y consultar QR","Funcionario — gestionar reportes y jornadas","Veterinario — vacunación, esterilización y datos sanitarios","Administrador — roles, auditoría e indicadores").forEach{b.addView(card().apply{addView(tv(it,13f,true))})};b.addView(card().apply{addView(tv("🔄 Datos y sincronización",13f,true));addView(tv("3 registros pendientes · modo offline",11f,false,gray))});b.addView(button("CERRAR SESIÓN"){Toast.makeText(this@MainActivity,"Sesión cerrada (demo)",Toast.LENGTH_SHORT).show()});c.addView(scroll(b));replace(c)}
}
