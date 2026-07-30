var WIKI = window.WIKI || {};

WIKI.ru = {
subtitle: "Вики · v1.1.0",
searchPlaceholder: "Поиск…",
noResults: "Ничего не найдено.",
copy: "Копировать",
copied: "Скопировано",
sections: [

/* ============================ НАЧАЛО ============================ */
{
group: "Начало", id: "overview", title: "Обзор",
lede: "Что делает CobbleTracker и один факт о Cobblemon, который объясняет половину мода.",
body: `
<p>CobbleTracker следит за каждым покемоном, попадающим в мир сервера, решает, относится ли он к одной из
ваших <b>категорий трекера</b>, и затем объявляет его, поднимает над ним луч, предлагает вейпоинт на
миникарте и записывает в просматриваемую историю.</p>

<p>А ещё он спавнит легендарных — потому что <b>Cobblemon этого не делает</b>.</p>

<div class="note"><b>Факт, определяющий всё остальное</b>
<p>Ванильный Cobblemon 1.7 содержит <b>ноль</b> записей о легендарных в своём пуле спавна. Там 816 файлов,
пронумерованных по Pok&eacute;dex, и среди них нет ни одной легендарки. Бакеты редкости — только
<code>common</code>, <code>uncommon</code>, <code>rare</code> и <code>ultra-rare</code>; бакета «legendary»
не существует. Из коробки легендарные попадают в мир <b>только</b> через шрайны или
<code>/pokespawn</code>.</p></div>

<p>Поэтому в CobbleTracker есть <a href="#legendary">директор легендарных</a>. И поскольку многие серверы
уже решают это датапаком, директор <b>автоматически отступает</b>, когда его обнаруживает.</p>

<h3>Из чего мод состоит</h3>
<table>
<tr><th>Часть</th><th>Где работает</th><th>Что нужно</th></tr>
<tr><td>Объявления, история, директор легендарных, хуки команд, Discord</td><td>Сервер</td><td>Ничего на клиенте</td></tr>
<tr><td>Луч, GUI истории, вейпоинты, режим охоты</td><td>Клиент</td><td>Мод с обеих сторон</td></tr>
<tr><td><code>/checklegendary</code>, <code>/lastlegend</code>, <code>/ct notify</code></td><td>Сервер</td><td>Обычный чат — работает с ванильным клиентом</td></tr>
</table>

<h3>Требования</h3>
<ul>
<li>Minecraft <b>1.21.1</b></li>
<li><b>Fabric</b> (+ Fabric API, Fabric Language Kotlin) или <b>NeoForge</b></li>
<li><b>Cobblemon 1.7.x</b> — собрано и проверено на 1.7.3</li>
<li><b>Architectury API</b></li>
<li>Java 21</li>
</ul>
`
},

{
group: "Начало", id: "install", title: "Установка",
lede: "Серверной части достаточно для большинства функций. Клиентская добавляет визуал.",
body: `
<h3>Сервер</h3>
<p>Положите jar в <code>mods/</code> рядом с Cobblemon, Architectury API и API вашего загрузчика.
При первом запуске CobbleTracker создаст три конфига с комментариями:</p>
<pre><code>config/cobbletracker/
  config.yml          правила сервера + категории трекера
  announcements.yml   как объявляется каждая категория
  legendaries.yml     правила встроенного директора легендарных</code></pre>

<h3>Клиент</h3>
<p>Тот же jar, та же папка. Без него игрок всё равно получает объявления в чат и все чат-команды;
с ним — ещё луч, GUI истории, вейпоинты и режим охоты.</p>

<div class="tip"><b>Смешанные серверы — норма</b>
<p>Сервер никогда не предполагает, что у клиента есть мод. Он узнаёт об этом при входе и шлёт пакеты
только тем клиентам, которые о себе сообщили.</p></div>

<h3>Проверка запуска</h3>
<pre><code>[Server thread/INFO]: CobbleTracker ready - 4 tracker categories loaded
[Server thread/INFO]: Spawner for 'legendaries' is active (mode AUTO)</code></pre>
<p>Вторая строка — директор сообщает, что не нашёл данных о спавне легендарных и взял эту работу на себя.
Если их даёт датапак, там будет <code>standing down</code>.</p>
`
},

{
group: "Начало", id: "quickstart", title: "Быстрый старт",
lede: "Пять минут до рабочей настройки.",
body: `
<h3>1. Посмотрите, что есть</h3>
<pre><code>/ct                 открыть GUI истории
/checklegendary     что может заспавниться здесь и когда
/ct admin           панель настроек (оператор)</code></pre>

<h3>2. Тестовый спавн</h3>
<pre><code>/ct fakehit</code></pre>
<p>Спавнит настоящего шайни-пикачу в шести блоках и прогоняет всю цепочку — объявление, луч, ссылку на
вейпоинт, запись в историю и отчёт о поимке/поражении/деспавне. Если что-то настроено неверно, видно
именно здесь.</p>

<h3>3. Принудительная легендарка</h3>
<pre><code>/spawnlegendary</code></pre>
<p>Тратит одну попытку сразу. Может вежливо отказать — это работа функции, а не сбой. Запустите
<code>/checklegendary</code>, чтобы увидеть, что допускает это место.</p>

<h3>4. Правка и перезагрузка</h3>
<pre><code>/ct reload</code></pre>
<p>Перечитывает все три YAML-файла. Если один не парсится, действующая конфигурация <b>сохраняется</b>, а
вы получаете сообщение со ссылкой на лог — лишняя запятая никогда не уронит ваши трекеры.</p>

<h3>5. Своя категория</h3>
<p>Два блока, по одному в каждом файле, с одинаковым ключом. Готовые примеры — в разделе
<a href="#recipes">Рецепты</a>.</p>
`
},

/* ============================== СПРАВОЧНИК =============================== */
{
group: "Справочник", id: "commands", title: "Команды",
lede: "Все команды, с пометкой, какие работают без клиентского мода.",
body: `
<table>
<tr><th>Команда</th><th>Действие</th><th>По умолчанию</th><th>Ванильный клиент?</th></tr>
<tr><td><code>/cobbletracker</code> <code>/ct</code> <code>/last</code> <code>/ll</code></td><td>Открыть GUI истории</td><td>все</td><td>нет</td></tr>
<tr><td><code>/lastlegend</code></td><td>Недавние легендарки — GUI на вкладке Legends или сводка в чат</td><td>все</td><td><b>да</b> (откат в чат)</td></tr>
<tr><td><code>/checklegendary</code></td><td>Что может заспавниться здесь, с шансами и таймером</td><td>все</td><td><b>да</b></td></tr>
<tr><td><code>/spawnlegendary</code></td><td>Потратить одну попытку немедленно</td><td>оператор</td><td><b>да</b></td></tr>
<tr><td><code>/ct notify</code></td><td>Показать ваши настройки по категориям</td><td>все</td><td><b>да</b></td></tr>
<tr><td><code>/ct notify &lt;кат&gt; on|off</code></td><td>Отключить категорию для себя</td><td>все</td><td><b>да</b></td></tr>
<tr><td><code>/ct notify &lt;кат&gt; sound on|off</code></td><td>Оставить текст, убрать звук</td><td>все</td><td><b>да</b></td></tr>
<tr><td><code>/ct notify &lt;кат&gt; radius &lt;n&gt;</code></td><td>Сузить до своего радиуса</td><td>все</td><td><b>да</b></td></tr>
<tr><td><code>/ct theme &lt;имя&gt;</code></td><td>Сменить тему GUI</td><td>все</td><td>нет</td></tr>
<tr><td><code>/ct waypoint &lt;id&gt;</code></td><td>Поставить прошлый спавн на миникарту</td><td>все</td><td>нет</td></tr>
<tr><td><code>/ct reload</code></td><td>Перезагрузить конфиги</td><td>оператор</td><td><b>да</b></td></tr>
<tr><td><code>/ct admin</code></td><td>Панель настроек</td><td>оператор</td><td>нет</td></tr>
<tr><td><code>/ct fakehit</code></td><td>Спавн тестового шайни-пикачу</td><td>оператор</td><td><b>да</b></td></tr>
</table>

<div class="note"><b>Почему часть команд намеренно только в чате</b>
<p><code>/checklegendary</code> и <code>/ct notify</code> пишут в чат, а не открывают экран. На большинстве
серверов у большинства игроков клиентского мода нет, а функция, до которой они не могут дотянуться, —
не функция.</p></div>
`
},

{
group: "Справочник", id: "permissions", title: "Права и LuckPerms",
lede: "Ноды используются автоматически при наличии LuckPerms, иначе — уровни оператора.",
body: `
<p>У CobbleTracker <b>нет зависимости сборки</b> от LuckPerms. Он ищет API во время работы и использует,
если тот есть. Ничего устанавливать и настраивать не нужно.</p>

<table>
<tr><th>Нода</th><th>Команда</th><th>Откат</th></tr>
<tr><td><code>cobbletracker.command.gui</code></td><td><code>/ct</code>, <code>/last</code>, <code>/ll</code></td><td>все</td></tr>
<tr><td><code>cobbletracker.command.lastlegend</code></td><td><code>/lastlegend</code></td><td>все</td></tr>
<tr><td><code>cobbletracker.command.checklegendary</code></td><td><code>/checklegendary</code></td><td>все</td></tr>
<tr><td><code>cobbletracker.command.notify</code></td><td><code>/ct notify</code></td><td>все</td></tr>
<tr><td><code>cobbletracker.command.waypoint</code></td><td><code>/ct waypoint</code></td><td>все</td></tr>
<tr><td><code>cobbletracker.command.theme</code></td><td><code>/ct theme</code></td><td>все</td></tr>
<tr><td><code>cobbletracker.command.spawnlegendary</code></td><td><code>/spawnlegendary</code></td><td>оператор (2)</td></tr>
<tr><td><code>cobbletracker.command.reload</code></td><td><code>/ct reload</code></td><td>оператор (2)</td></tr>
<tr><td><code>cobbletracker.command.admin</code></td><td><code>/ct admin</code></td><td>оператор (2)</td></tr>
<tr><td><code>cobbletracker.command.fakehit</code></td><td><code>/ct fakehit</code></td><td>оператор (2)</td></tr>
</table>

<h3>Три состояния, а не два</h3>
<ul>
<li><b>true</b> — разрешено, даже без оператора.</li>
<li><b>false</b> — запрещено, <b>даже оператору</b>. Ради этого и ставят плагин прав.</li>
<li><b>Не задано</b> — откат к уровню оператора из таблицы. Это <b>не</b> означает «запретить».</li>
</ul>

<div class="warn"><b>Почему «не задано» обязано означать откат</b>
<p>Если бы незаданная нода запрещала, установка LuckPerms мгновенно отрезала бы всех игроков от
<code>/ct</code>, пока админ вручную не выдаст десяток нод. Откат означает, что установка LuckPerms
ничего не меняет, пока вы сами что-то не настроите.</p></div>

<h3>Примеры</h3>
<pre><code>/lp group default permission set cobbletracker.command.gui true
/lp group vip permission set cobbletracker.command.spawnlegendary true
/lp user Steve permission set cobbletracker.command.notify false</code></pre>

<p>Консоль и командные блоки проверяются только по ванильному уровню — ноды относятся к игрокам, а консоль
должна продолжать работать.</p>
`
},

{
group: "Справочник", id: "config", title: "config.yml",
lede: "Правила сервера и категории трекера, на которых держится всё остальное.",
body: `
<h3>general-settings</h3>
<pre><code>general-settings:
  chat-prefix: "&lt;bold&gt;&lt;gradient:#6C5CE7:#38BDF8&gt;[CobbleTracker]&lt;/gradient&gt;&lt;/bold&gt; "
  hide-exact-position: false
  show-title: false</code></pre>
<table>
<tr><th>Ключ</th><th>Значение</th></tr>
<tr><td><code>chat-prefix</code></td><td>MiniMessage-префикс во всех строках мода. Старые <code>&amp;</code>-коды тоже работают.</td></tr>
<tr><td><code>hide-exact-position</code></td><td><code>true</code> округляет все координаты до центра чанка — чат, луч, вейпоинт, GUI истории <b>и</b> Discord. Операторы в GUI по-прежнему видят точные.</td></tr>
<tr><td><code>show-title</code></td><td>Дополнительно показывать title/subtitle из <code>announcements.yml</code> на экране.</td></tr>
</table>

<h3>beam</h3>
<pre><code>beam:
  enabled: true
  radius: 0            # 0 = авто (ваша дальность прорисовки)
  duration-seconds: 600
  height: 512</code></pre>
<div class="note"><b>Намеренно нет в <code>/ct admin</code></b>
<p>Каждый спавн передаёт свои настройки луча клиенту собственным пакетом в момент появления. Поэтому
правка в живой панели повлияла бы только на <i>следующий</i> спавн, при этом выглядя так, будто она не
делает ничего. Эти ключи живут здесь и применяются по <code>/ct reload</code>.</p></div>

<h3>minimap</h3>
<pre><code>minimap:
  enabled: true
  xaero: true
  voxelmap: true
  journeymap: true
  use-beam-color: true</code></pre>

<h3>hunt</h3>
<pre><code>hunt:
  enabled: true</code></pre>
<p>Слово сервера о клиентском меню <code>\\</code>. Поставьте <code>false</code> там, где поиск редких спавнов
должен быть вызовом: меню не откроется, лучи охоты останутся выключены. Сохранённый список видов
игрока <b>не стирается</b> и снова работает на сервере, который это разрешает.</p>

<h3>trackers</h3>
<pre><code>trackers:
  legendaries:
    name: "Legends"              # подпись в GUI
    spec: "isLegendary:true"     # см. раздел «Язык спеков»
    color: "#FF3333"             # цвет тира: луч, плашка, вейпоинт
    max-stored: 10               # лимит истории, вытесняется старейшее. 0 = объявлять, но не хранить
    blacklist: "magikarp"        # список через запятую, никогда не записывается
    enabled: true
    dimensions: ""               # id измерений через запятую; пусто = везде
    on-spawn-commands: []
    on-catch-commands: []
    spawn:
      enabled: true
      mode: auto                 # auto | always | never
      context-aware: true
      interval-ticks: 36000      # 20 тиков = 1 секунда
      chance: 0.30
      distribute-among-players: true
      player-cooldown-ticks: 108000
      min-distance: 32
      max-distance: 80
      level: "55-75"             # диапазон или одно число
      shiny: false
      species-pool: ""           # пусто = вывести из категории</code></pre>

<div class="tip"><b>Порядок важен</b>
<p>Категории проверяются <b>сверху вниз, до первого совпадения</b>. Шайни-стартер попадёт в ту из
<code>shinies</code> / <code>starters</code>, что стоит выше. Поменяйте порядок или сузьте отрицанием:
<code>spec: "label:starter !isShiny"</code>.</p></div>
`
},

{
group: "Справочник", id: "announcements", title: "announcements.yml",
lede: "Как объявляется каждая категория. Ключи должны точно совпадать с id категорий.",
body: `
<pre><code>notifications:
  legendaries:
    enabled: true
    title: "&lt;gold&gt;&lt;bold&gt;LEGENDARY SPAWN&lt;/bold&gt;&lt;/gold&gt;"
    subtitle: "&lt;aqua&gt;%species%&lt;/aqua&gt; появился в &lt;green&gt;%biome%&lt;/green&gt;!"
    chat: "&lt;white&gt;Дикий&lt;/white&gt; &lt;aqua&gt;%species%&lt;/aqua&gt; &lt;gray&gt;на&lt;/gray&gt; %waypoint%"
    actionbar: ""
    sound: "cobblemon:pc.on"
    sound-volume: 1.0
    sound-pitch: 1.0
    waypoint: true
    play-to-all: true
    broadcast-radius: 0
    discord-webhook: ""
    discord-template: "**%species%** в %biome% на %x%, %y%, %z%"</code></pre>

<div class="warn"><b>Категория без блока здесь молчит</b>
<p>Неизвестный id разрешается в «выключено», а это гасит и строку в чате, <b>и луч</b>. Если вы добавили
категорию, и она пишется в историю, но ничего не объявляет — причина в этом.</p></div>

<h3>Каналы</h3>
<table>
<tr><th>Ключ</th><th>Примечания</th></tr>
<tr><td><code>chat</code></td><td>Основная строка. Поддерживает кликабельный <code>%waypoint%</code>.</td></tr>
<tr><td><code>title</code> / <code>subtitle</code></td><td>Показываются только при <code>show-title: true</code> в config.yml.</td></tr>
<tr><td><code>actionbar</code></td><td>Над хотбаром. <b>Не зависит</b> от <code>show-title</code> — тихий вариант. Не кликабельно, поэтому <code>%waypoint%</code> выводится как обычные координаты.</td></tr>
<tr><td><code>sound</code></td><td>Любой id звука. <code>sound-volume: 0</code> — намеренное отключение, а не «по умолчанию».</td></tr>
<tr><td><code>discord-webhook</code></td><td>См. <a href="#discord">Discord</a>.</td></tr>
</table>

<h3>Плейсхолдеры</h3>
<p><code>%species%</code> <code>%biome%</code> <code>%world%</code> <code>%x%</code> <code>%y%</code>
<code>%z%</code> <code>%waypoint%</code></p>

<h3>Охват</h3>
<table>
<tr><th><code>play-to-all</code></th><th><code>broadcast-radius</code></th><th>Результат</th></tr>
<tr><td>true</td><td>0</td><td>Весь сервер</td></tr>
<tr><td>true / false</td><td>&gt; 0</td><td>Только игроки в N блоках, то же измерение</td></tr>
<tr><td>false</td><td>0</td><td>Неявный радиус 128 блоков</td></tr>
</table>

<div class="note"><b>Радиус соблюдается везде</b>
<p>Категория с радиусом ограничивает и свою <i>историю</i>: её прошлые спавны появляются в GUI только у
тех, кто рядом. GUI никогда не становится обходным путём для намеренно локального объявления.</p></div>

<h3>Форматирование</h3>
<p>MiniMessage: <code>&lt;gold&gt;</code>, <code>&lt;bold&gt;</code>, <code>&lt;#RRGGBB&gt;</code>,
<code>&lt;gradient:#a:#b&gt;&hellip;&lt;/gradient&gt;</code>. Старые коды <code>&amp;6&amp;l</code> конвертируются
автоматически.</p>
`
},

{
group: "Справочник", id: "legendaries-yml", title: "legendaries.yml",
lede: "Где и когда встроенный директор может разместить каждого легендарного.",
body: `
<p>Используется <b>только</b> пока директор реально работает. При <code>mode: auto</code> файл игнорируется,
как только обнаружен датапак со спавном легендарных.</p>

<pre><code>legendaries:
  - species: "cobblemon:rayquaza"
    weight: 1.0
    biomes: ["#cobblemon:is_peak", "#cobblemon:is_mountain"]
    time: day
    isRaining: false
    minY: 120

  - species: "cobblemon:lugia"
    biomes: ["#cobblemon:is_ocean"]
    time: night

  - species: "cobblemon:zapdos"
    biomes: ["#cobblemon:is_highlands", "#cobblemon:is_plains"]
    isThundering: true</code></pre>

<table>
<tr><th>Ключ</th><th>Значение</th></tr>
<tr><td><code>species</code></td><td>Id с неймспейсом. Обязателен.</td></tr>
<tr><td><code>weight</code></td><td>Относительная вероятность среди подходящих правил. По умолчанию 1.0.</td></tr>
<tr><td><code>biomes</code></td><td>Id биомов и теги <code>#namespace:tag</code>. Пусто = где угодно.</td></tr>
<tr><td><code>dimensions</code></td><td>Id измерений. Пусто = любое.</td></tr>
<tr><td><code>time</code></td><td><code>day</code>, <code>night</code> или <code>any</code>.</td></tr>
<tr><td><code>isRaining</code> / <code>isThundering</code></td><td><code>true</code>, <code>false</code>, либо опустить для «неважно».</td></tr>
<tr><td><code>minY</code> / <code>maxY</code></td><td>Диапазон высоты для позиции спавна.</td></tr>
</table>

<div class="tip"><b>Тот же язык, что у Cobblemon</b>
<p>Эти ключи намеренно повторяют JSON условий спавна самого Cobblemon, так что тег биома из вашего
spawn-файла работает здесь без изменений. Cobblemon поставляет 55 тегов биомов:
<code>#cobblemon:is_ocean</code>, <code>is_mountain</code>, <code>is_desert</code>, <code>is_cave</code>,
<code>is_freezing</code>, <code>is_jungle</code>, <code>is_magical</code>, <code>is_deep_dark</code>&hellip;</p></div>

<div class="warn"><b>Спавнятся только реализованные виды</b>
<p>В Cobblemon 1.7.3 реализовано <b>16</b> легендарных: articuno, ho-oh, latias, latios, lugia, mewtwo,
moltres, rayquaza, regice, regidrago, regieleki, regigigas, regirock, registeel, xerneas, zapdos
(плюс 2 мифических и 2 ультра-биста). Правило с любым другим видом просто никогда не сработает.</p></div>

<p>Опечатка в <code>isRaining</code> <b>пишется в лог</b>, а не превращается молча в «неважно» — иначе правило,
задуманное для дождя, тихо срабатывало бы в ясную погоду, и это было бы трудно заметить.</p>
`
},

{
group: "Справочник", id: "spec", title: "Язык спеков",
lede: "Матчер, на котором строится каждая категория. Одна строка определяет, что она такое.",
body: `
<p>Спек — это список токенов через пробел, все объединяются по <b>И</b>.</p>
<pre><code>spec: "isShiny:true level:>50"</code></pre>

<h3>Токены о самом покемоне</h3>
<table>
<tr><th>Токен</th><th>Пример</th><th>Примечания</th></tr>
<tr><td><code>isShiny</code></td><td><code>isShiny</code> или <code>isShiny:true</code></td><td>Голый токен означает true</td></tr>
<tr><td><code>isLegendary</code></td><td><code>isLegendary:true</code></td><td>Метка вида</td></tr>
<tr><td><code>isMythical</code></td><td></td><td>Метка вида</td></tr>
<tr><td><code>isUltraBeast</code></td><td></td><td>Метка вида</td></tr>
<tr><td><code>isBoss</code></td><td></td><td>Метка или аспект</td></tr>
<tr><td><code>species</code></td><td><code>species:rayquaza</code></td><td>Голое имя или id с неймспейсом</td></tr>
<tr><td><code>label</code></td><td><code>label:starter</code></td><td>См. <a href="#labels">Метки</a></td></tr>
<tr><td><code>aspect</code></td><td><code>aspect:alolan</code></td><td>Аспекты экземпляра — способ ловить региональные формы</td></tr>
<tr><td><code>form</code></td><td><code>form:alola</code></td><td>Имя формы в нижнем регистре</td></tr>
<tr><td><code>gender</code></td><td><code>gender:female</code></td><td><code>male</code> / <code>female</code> / <code>genderless</code></td></tr>
<tr><td><code>nature</code></td><td><code>nature:adamant</code></td><td>Только путь, без неймспейса</td></tr>
<tr><td><code>ability</code></td><td><code>ability:levitate</code></td><td></td></tr>
<tr><td><code>level</code></td><td><code>level:&gt;50</code></td><td></td></tr>
<tr><td><code>perfectIvs</code></td><td><code>perfectIvs:&gt;=4</code></td><td>Сколько IV равны 31</td></tr>
<tr><td><code>ivs</code></td><td><code>ivs:&gt;=150</code></td><td>Сумма всех шести, максимум 186</td></tr>
<tr><td><code>scale</code></td><td><code>scale:&gt;1.15</code></td><td>Модификатор размера; 1.0 — стандарт</td></tr>
</table>

<h3>Токены об обстоятельствах</h3>
<table>
<tr><th>Токен</th><th>Пример</th><th>Примечания</th></tr>
<tr><td><code>biome</code></td><td><code>biome:jungle</code></td><td>Голое имя = любой неймспейс; <code>minecraft:jungle</code> = ровно этот</td></tr>
<tr><td><code>dimension</code></td><td><code>dimension:minecraft:the_nether</code></td><td></td></tr>
<tr><td><code>time</code></td><td><code>time:night</code></td><td><code>day</code> или <code>night</code></td></tr>
<tr><td><code>weather</code></td><td><code>weather:thunder</code></td><td><code>rain</code>, <code>thunder</code> или <code>clear</code></td></tr>
<tr><td><code>y</code></td><td><code>y:&lt;0</code></td><td>Высота спавна</td></tr>
</table>

<h3>Сравнения</h3>
<p>Числовые токены принимают <code>&gt;</code>, <code>&lt;</code>, <code>&gt;=</code>, <code>&lt;=</code> или
просто число для равенства. Равенство сравнивается с небольшим допуском, поэтому <code>scale:1.15</code>
совпадает с покемоном, у которого float-масштаб равен 1.15.</p>

<h3>Операторы</h3>
<p>Префикс <b><code>!</code></b> отрицает токен, а <b><code>|</code></b> внутри значения означает
<i>или</i>:</p>
<pre><code>spec: "isShiny !species:magikarp"
spec: "isLegendary species:rayquaza|lugia"
spec: "level:&gt;50|&lt;10"
spec: "isShiny time:night biome:swamp"</code></pre>

<div class="note"><b>Оба оператора работают с любым токеном</b>
<p>Они применяются при компиляции спека, а не внутри отдельных обработчиков, поэтому любой токен —
включая добавленные в будущих версиях — их поддерживает.</p></div>

<div class="warn"><b>Ошибки заметны, а не молчаливы</b>
<p>Неизвестный токен, нечитаемое булево (<code>isShiny:maybe</code>) или пустая альтернатива
(<code>a||b</code>) заставляют категорию не совпадать <b>ни с чем</b> и пишут причину в лог. Она никогда не
начинает молча совпадать со всем подряд.</p></div>

<h3>Связь со встроенным спавнером</h3>
<p>Спавнер не разместит то, что его же категория отвергнет. Если написать спек, который спавнер не может
удовлетворить — например <code>isBoss:true</code>, — он предупредит <b>один раз</b> и ничего не разместит.
Отрицательный токен вроде <code>!isShiny</code> считается «нет указания», поэтому решает
<code>spawn.shiny</code>.</p>
`
},

{
group: "Справочник", id: "labels", title: "Метки Cobblemon",
lede: "Что реально ловит label:, по данным Cobblemon 1.7.3 — включая ловушку.",
body: `
<p>Счётчики — <b>реализованные виды</b> в Cobblemon 1.7.3 (всего 851).</p>

<table>
<tr><th>Метка</th><th>Видов</th><th>Применение</th></tr>
<tr><td><code>gen1</code></td><td>151</td><td rowspan="9">Группы по поколениям</td></tr>
<tr><td><code>gen2</code></td><td>96</td></tr>
<tr><td><code>gen3</code></td><td>119</td></tr>
<tr><td><code>gen4</code></td><td>85</td></tr>
<tr><td><code>gen5</code></td><td>135</td></tr>
<tr><td><code>gen6</code></td><td>63</td></tr>
<tr><td><code>gen7</code></td><td>54</td></tr>
<tr><td><code>gen8</code></td><td>67</td></tr>
<tr><td><code>gen9</code></td><td>75</td></tr>
<tr><td><code>starter</code></td><td>27</td><td>Только базовые формы стартеров</td></tr>
<tr><td><code>legendary</code></td><td>16</td><td></td></tr>
<tr><td><code>fossil</code></td><td>21</td><td></td></tr>
<tr><td><code>baby</code></td><td>19</td><td></td></tr>
<tr><td><code>powerhouse</code></td><td>9</td><td></td></tr>
<tr><td><code>restricted</code></td><td>5</td><td>Коробочные легендарки</td></tr>
<tr><td><code>mythical</code></td><td>2</td><td></td></tr>
<tr><td><code>ultra_beast</code></td><td>2</td><td></td></tr>
<tr><td><code>paradox</code></td><td>2</td><td></td></tr>
<tr><td><code>kantonian_form</code></td><td>37</td><td rowspan="3">Базовые региональные варианты</td></tr>
<tr><td><code>unovan_form</code></td><td>14</td></tr>
<tr><td><code>johtonian_form</code></td><td>9</td></tr>
</table>

<div class="warn"><b>Ловушка: <code>label:</code> читает <i>вид</i>, а не экземпляр</b>
<p><code>mega</code>, <code>gmax</code>, <code>hisuian_form</code> и <code>galarian_form</code> объявлены на
<b>формах</b>, а не на виде. <code>label:</code> сопоставляет метки самого вида, поэтому
<code>label:mega</code> и <code>label:hisuian_form</code> не совпадают <b>ни с чем</b>.</p>
<p>Например, у алольской Vulpix метки вида остаются <code>gen1, kantonian_form</code>. Алольской её делает
<b>аспект</b>.</p></div>

<div class="tip"><b>Для вариантов используйте <code>aspect:</code> или <code>form:</code></b>
<pre><code>spec: "aspect:alolan"     # любая алольская форма
spec: "form:alola"        # то же, по имени формы
spec: "aspect:hisuian"
spec: "aspect:galarian"</code></pre></div>

<h3>Как посмотреть метки самому</h3>
<p>Файлы видов Cobblemon лежат в его jar по пути
<code>data/cobblemon/species/generationN/&lt;имя&gt;.json</code>. Массив <code>labels</code> верхнего уровня —
это то, что видит <code>label:</code>; всё внутри <code>forms[]</code> — нет.</p>
`
},

/* ============================== ФУНКЦИИ ================================ */
{
group: "Функции", id: "legendary", title: "Директор легендарных",
lede: "Как мод решает когда, где и кого — и когда уходит с дороги.",
body: `
<h3>Зачем он нужен</h3>
<p>У ванильного Cobblemon нет данных о спавне легендарных вообще. Без датапака или этого директора
легендарные появляются только из шрайнов.</p>

<h3>Три режима</h3>
<table>
<tr><th>Режим</th><th>Поведение</th></tr>
<tr><td><code>auto</code> <i>(по умолчанию)</i></td><td>Работает, только пока в живом пуле спавна Cobblemon <b>нет</b> записей о легендарных. Как только датапак их добавляет, директор отступает.</td></tr>
<tr><td><code>always</code></td><td>Работает всегда. Если вы намеренно хотите оба источника.</td></tr>
<tr><td><code>never</code></td><td>Спит. Конфиг остаётся, ничего не спавнится.</td></tr>
</table>

<p>Решение пишется в лог при каждом изменении, чтобы это никогда не было молчаливым бездействием:</p>
<pre><code>Spawner for 'legendaries' is active (mode AUTO)
Spawner for 'legendaries' is standing down: Cobblemon's own spawn data
  already covers this tier (mode 'auto')</code></pre>

<h3>Как выбирается спавн</h3>
<ol>
<li>Каждые <code>interval-ticks</code> — один бросок <code>chance</code> на весь сервер (при
<code>distribute-among-players</code>), затем случайный подходящий игрок становится <b>якорем</b>.</li>
<li>Выбирается позиция между <code>min-distance</code> и <code>max-distance</code> от якоря.</li>
<li><b>Правила проверяются в этой позиции</b>, а не у игрока — они регулярно оказываются в разных биомах.
Перебирается несколько позиций, прежде чем сдаться.</li>
<li>Среди подходящих правил одно выбирается по <code>weight</code>.</li>
<li>Собранный покемон всё равно должен удовлетворить спек самой категории, прежде чем будет размещён.</li>
</ol>

<div class="tip"><b>«Ничего не подходит» — это функция</b>
<p>Если ни одно правило не подходит там, куда упал бы спавн, ничего не размещается. Regice в пустыне
формально был бы легендарным спавном — и заодно сделал бы всю систему бессмысленной.</p></div>

<h3>Справедливость</h3>
<ul>
<li><code>distribute-among-players: true</code> — один бросок на сервер, поэтому частота не растёт вместе
с числом игроков. Поставьте <code>false</code> для отдельного броска на каждого.</li>
<li><code>player-cooldown-ticks</code> — игрок, только что бывший якорем, пропускается на это время, чтобы
все спавны не доставались одному.</li>
</ul>

<h3>Настройка частоты</h3>
<p>Ожидаемых спавнов в час &asymp; <code>3600 / (interval-ticks / 20) &times; chance</code>.</p>
<table>
<tr><th>Ощущение</th><th>interval-ticks</th><th>chance</th><th>Примерно</th></tr>
<tr><td>Уровень события</td><td>72000 (1 ч)</td><td>0.15</td><td>один в ~7 часов</td></tr>
<tr><td>По умолчанию</td><td>36000 (30 мин)</td><td>0.30</td><td>один в ~1.7 часа</td></tr>
<tr><td>Людный сервер</td><td>18000 (15 мин)</td><td>0.40</td><td>один в ~40 минут</td></tr>
</table>
`
},

{
group: "Функции", id: "checklegendary", title: "/checklegendary",
lede: "Читает живой пул спавна Cobblemon — поэтому говорит правду о том, что у вас установлено.",
body: `
<pre><code>Legendary spawns - overworld, plains, night, thunderstorm
  &#10004; Zapdos       ultra-rare &middot; ~0.02% per attempt
  &#10004; Regieleki    ultra-rare &middot; ~0.02% per attempt
  6 other legendaries need a different biome, time or weather.
  Next attempt: 12m 30s &middot; chance 30%</code></pre>

<p>Он <b>не</b> парсит spawn-JSON сам. Он читает реестр, который Cobblemon уже загрузил, объединил и
проверил — поэтому работает <b>любой</b> датапак, в любом неймспейсе.</p>

<h3>Что оценивается</h3>
<p>Биом (включая теги), время суток, погода, измерение, высота, уровень света, видимость неба и фаза
луны — по каждому условию отдельно, ровно как это делает спавнер самого Cobblemon. Антиусловия
инвертируют.</p>

<div class="note"><b>«(some conditions unchecked)»</b>
<p>Структуры, маркеры и slime-чанки требуют реальной позиции спавна и не могут быть оценены из команды.
Когда запись зависит от такого условия, отчёт об этом сообщает, а не изображает уверенность.</p></div>

<h3>Два разных отчёта</h3>
<ul>
<li><b>В пуле есть легендарные</b> — перечисляет их с приблизительным шансом на попытку.</li>
<li><b>Пул пуст</b> — сообщает об этом и перечисляет правила директора, подходящие там, где вы стоите.</li>
</ul>

<h3>Заодно диагностика</h3>
<p>Если вы поставили датапак с легендарными, а команда всё ещё пишет <i>«Cobblemon has no legendary spawns
loaded»</i>, значит датапак не подхватился. Обычная причина — он написан под старый Cobblemon: в 1.7 ключ
спавна <code>context</code> переименован в <code>spawnablePositionType</code>, и записи со старым именем
отбрасываются при загрузке.</p>

<h3>О шансах</h3>
<p>Цифра — это доля бакета &times; вес записи среди всего подходящего в этой точке. Это приближение —
реальный спавнер ещё учитывает типы позиций и пробует несколько точек за попытку — но оно построено на
живых данных, а не выдумано.</p>
`
},

{
group: "Функции", id: "beam", title: "Луч над спавном",
lede: "Работает от присутствия, а не по таймеру.",
body: `
<p>Колонна в стиле маяка стоит над отслеживаемым покемоном, пока он в ваших загруженных чанках, и
окрашена в цвет тира категории.</p>

<h3>Как работает grace</h3>
<p><code>duration-seconds</code> — это <b>keep-alive</b>, а не время жизни. Каждый кадр, пока покемон
виден, дедлайн отодвигается. Поэтому луч:</p>
<ul>
<li>показывается, когда вы рядом;</li>
<li>переживает отлёт и возвращение внутри окна;</li>
<li>гаснет только после того, как покемон был вне зоны всё окно целиком;</li>
<li>и исчезает <b>сразу</b> при настоящей поимке, поражении или деспавне — не по истечении grace.</li>
</ul>

<h3>Перезаход</h3>
<p>Лучи заново отправляются при входе, поэтому перезаход не оставляет легендарку стоять без отметки.
Повторная отправка подчиняется тем же правилам, что и исходное объявление — отключённая вами категория
останется молчаливой.</p>

<h3>Радиус</h3>
<p><code>radius: 0</code> означает «ваша дальность прорисовки» — дальше покемон на клиенте всё равно не
существует. Конкретное число ограничивает жёстче.</p>

<div class="note"><b>Никаких призрачных лучей</b>
<p>Сервер отличает настоящий деспавн от простой выгрузки чанка из-за того, что все улетели. Он сообщает
«despawned», только если игрок был достаточно близко, чтобы это засвидетельствовать — иначе продолжает
следить, и луч возвращается, когда кто-то приходит обратно.</p></div>

<h3>Клиентский переключатель</h3>
<p>В экране охоты есть кнопка <b>Beam: ON/OFF</b>. Она локальна для клиента и никогда не доходит до
сервера.</p>
`
},

{
group: "Функции", id: "minimap", title: "Вейпоинты на миникарте",
lede: "Xaero's, VoxelMap и JourneyMap — только те, что у вас реально есть.",
body: `
<p>Объявления могут нести кликабельную ссылку <b>Create Waypoint</b>. Клик ставит спавн на вашу
миникарту. Клик по карточке в GUI истории делает то же для <b>прошлого</b> спавна.</p>

<h3>Как работает определение</h3>
<p>Клиент сообщает серверу, какие моды миникарт у него есть, при входе. Сервер отправляет только эти
форматы. Миникарта не обязательна — без неё вы всё равно получаете луч, а клик по ссылке прямо говорит,
что ничего не установлено.</p>

<p>Вейпоинты передаются через собственный стабильный чат-формат каждого мода, поэтому у CobbleTracker нет
зависимости сборки ни от одного из них.</p>

<div class="tip"><b><code>/ct admin</code> показывает правду</b>
<p>Каждая строка миникарты помечается <i>(not installed)</i> и показана выключенной, если <b>у вас</b>
этого мода нет. Формат, включённый на сервере, но не установленный ни у кого — это переключатель, который
ничего не делает, и читать его как «вейпоинты работают» — неверный вывод.</p></div>

<p><code>use-beam-color: true</code> окрашивает маркер в цвет тира. Xaero's учитывает цвет; общий формат
VoxelMap/JourneyMap использует свой собственный.</p>
`
},

{
group: "Функции", id: "notify", title: "Настройки уведомлений игрока",
lede: "Чтобы оператору никогда не приходилось отключать категорию для всех.",
body: `
<p>Каждый игрок управляет своими уведомлениями по категориям — через вкладку <b>NOTIFY</b> в GUI или
<code>/ct notify</code> в чате.</p>

<pre><code>/ct notify                          показать ваши настройки
/ct notify shinies off              отключить shinies для себя
/ct notify shinies sound off        оставить текст, убрать звук
/ct notify legendaries radius 300   только в 300 блоках от вас</code></pre>

<h3>Что покрывает «off»</h3>
<p>Всё для этой категории: чат, звук, title, actionbar, луч и отчёт о поимке/поражении/деспавне. Есть
единая точка, решающая, до кого доходит спавн, поэтому мимо неё ничего не просачивается.</p>

<h3>Радиус только сужает</h3>
<p>Если сервер вещает категорию всем, а вы поставили <code>radius 300</code>, вы получите только близкие.
Если сервер уже ограничил её 200 блоками, а вы поставили 5000, вы всё равно получите 200. Личная
настройка не может стать способом увидеть намеренно локальное объявление издалека.</p>

<h3>Независимость</h3>
<p>Три настройки не мешают друг другу. Отключение звука у категории, которую вы заглушили, не включает её
обратно, а включение категории сохраняет заданный ранее радиус.</p>

<p>Настройки хранятся в <code>prefs.json</code> и по умолчанию — всё включено. Записываются только
отличия от умолчания, поэтому нетронутый игрок ничего не стоит.</p>
`
},

{
group: "Функции", id: "hunt", title: "Личный режим охоты",
lede: "Клиентское отслеживание того, что вам нужно. Клавиша \\.",
body: `
<p>Нажмите <code>\\</code>, чтобы открыть <b>Track a Pok&eacute;mon</b>, найти нужный вид в полном списке и
кликом включить охоту. Подходящие покемоны рядом получают янтарный луч и направляющие стрелки в углу HUD
с актуальными расстояниями.</p>

<ul>
<li>Полностью на клиенте — сканируются сущности, которые у клиента уже есть, поэтому нет обращения к
серверу и нечего настраивать.</li>
<li>Список <b>сохраняется между сессиями</b> в <code>hunt.json</code>.</li>
<li><b>Shift-клик по карточке</b> в GUI истории начинает охоту на этот вид — именно в истории замечаешь,
что «оно постоянно спавнится, а я всё пропускаю».</li>
<li>Отслеживаемые виды подсвечиваются в списке истории, так что она заодно показывает, за чем вы уже
следите.</li>
</ul>

<h3>Отключение на сервере</h3>
<pre><code>hunt:
  enabled: false</code></pre>
<p>На сервере, где поиск редких спавнов должен быть вызовом, это законное желание. Меню <code>\\</code>
тогда не открывается, а лучи охоты не горят.</p>

<div class="note"><b>Это просьба, а не принуждение</b>
<p>Охота работает по данным, которые у клиента уже есть, поэтому это правило, которое клиент соблюдает, а
не то, что сервер технически может запретить. Список видов игрока сохраняется в любом случае и снова
работает на сервере, который это разрешает.</p></div>

<p>Клавиша — обычный бинд Minecraft; переназначьте или очистите его в <b>Настройки &rarr; Управление</b>
в разделе CobbleTracker.</p>
`
},

{
group: "Функции", id: "hooks", title: "Хуки команд",
lede: "Награды, экономика и права без единой дополнительной зависимости.",
body: `
<pre><code>trackers:
  legendaries:
    on-spawn-commands:
      - "say Легендарный %species% появился в %biome%!"
    on-catch-commands:
      - "give %player% minecraft:diamond 3"
      - "lp user %player% permission set someperk.node true"</code></pre>

<table>
<tr><th>Плейсхолдер</th><th>Значение</th></tr>
<tr><td><code>%player%</code></td><td>Поймавший. Пусто в <code>on-spawn-commands</code> — спавн ещё никому не принадлежит.</td></tr>
<tr><td><code>%species%</code></td><td>Отображаемое имя</td></tr>
<tr><td><code>%category%</code></td><td>Id категории</td></tr>
<tr><td><code>%world%</code> <code>%biome%</code></td><td>Id с неймспейсом</td></tr>
<tr><td><code>%x%</code> <code>%y%</code> <code>%z%</code></td><td>Позиция спавна</td></tr>
</table>

<p>Команды выполняются от консоли сервера с уровнем прав 2 и <b>позиционированы в точке спавна</b> —
поэтому <code>~ ~ ~</code> и <code>execute</code> работают ожидаемо.</p>

<div class="warn"><b>Правка config.yml равносильна доступу оператора</b>
<p>Тот, кто может редактировать этот файл, может выполнить любую команду от консоли. Относитесь к праву
записи соответственно.</p></div>

<h3>Безопасность</h3>
<ul>
<li><code>%player%</code> или <code>%species%</code>, не являющиеся обычным идентификатором, приводят к
<b>пропуску команды с записью в лог</b>, а не к подстановке в командную строку. Никакое экранирование не
делает произвольную строку там безопасной.</li>
<li>Хук, вызвавший ещё один отслеживаемый спавн, не запустит хуки повторно — без этой защиты
<code>on-spawn-commands: ["pokespawn rayquaza"]</code> на категории легендарных превращается в
бесконечный цикл.</li>
<li>Не более 32 команд на событие; остальные пропускаются с записью в лог.</li>
<li>Одна сломанная команда никогда не стоит остальным.</li>
</ul>
`
},

{
group: "Функции", id: "discord", title: "Discord-вебхуки",
lede: "По категориям, вне серверного потока.",
body: `
<pre><code>notifications:
  legendaries:
    discord-webhook: "https://discord.com/api/webhooks/&hellip;"
    discord-template: "**%species%** появился в %biome% на %x%, %y%, %z%"</code></pre>

<p>Обычный markdown Discord, не MiniMessage. Плейсхолдеры: <code>%species%</code>, <code>%category%</code>,
<code>%biome%</code>, <code>%world%</code>, <code>%x%</code>, <code>%y%</code>, <code>%z%</code>.</p>

<h3>Поведение под нагрузкой</h3>
<ul>
<li>Отправка из отдельного потока с ограниченной очередью — медленный или недоступный Discord никогда не
стоит времени тика.</li>
<li>При переполнении очереди отбрасываются самые старые сообщения, и лог периодически об этом сообщает.</li>
<li>URL вебхука — это учётные данные, и он <b>никогда не пишется в лог</b>, даже при ошибке.</li>
<li>Принимаются только <code>https://</code> адреса.</li>
</ul>

<div class="note"><b>Приватность сохраняется</b>
<p>Координаты проходят то же округление, что и чат, поэтому <code>hide-exact-position: true</code>
действует и на Discord. Вебхук не может стать обходным путём.</p></div>

<h3>Типичные схемы</h3>
<p>Публичный канал для легендарных и канал для стаффа для всего остального: дайте каждой категории свой
вебхук. Оставьте <code>discord-webhook: ""</code> у тех, что публиковать не нужно.</p>
`
},

/* =============================== РУКОВОДСТВА ================================= */
{
group: "Руководства", id: "recipes", title: "Рецепты",
lede: "Готовые категории и схемы, включая неочевидные, о которых спрашивают.",
body: `
<p>Каждый рецепт — блок для <code>trackers:</code>. <b>Каждому также нужен блок в
<code>announcements.yml</code> с тем же ключом</b>, иначе он будет писать в историю молча. Порядок важен —
выигрывает первое совпадение.</p>

<div class="recipe">
<h4>Стартеры</h4>
<p class="why">У Cobblemon уже есть метка, так что это одна строка.</p>
<pre><code>  starters:
    name: "Starters"
    spec: "label:starter"
    color: "#4ADE80"
    max-stored: 15</code></pre>
<p><b>Нюанс:</b> метка стоит <b>только на 27 базовых формах</b> — у Ivysaur, Venusaur и Charizard её нет.
Для диких спавнов обычно это как раз то, что нужно.</p>
</div>

<div class="recipe">
<h4>Псевдолегендарные</h4>
<p class="why">Метки для них не существует, поэтому линия перечисляется вручную.</p>
<pre><code>  pseudolegends:
    name: "Pseudo-Legends"
    spec: "species:dratini|larvitar|bagon|beldum|gible|deino|goomy|jangmoo|dreepy|frigibax"
    color: "#A78BFA"
    max-stored: 10</code></pre>
<p><b>Нюанс:</b> id <b>без дефисов</b> — <code>jangmoo</code>, а не <code>jangmo-o</code>. Линия Frigibax в
1.7.3 ещё не реализована; оставить её безвредно. Добавьте финальные эволюции, если дикий Dragonite тоже
должен объявляться:
<code>&hellip;|dragonite|tyranitar|salamence|metagross|garchomp|hydreigon|goodra|kommoo|dragapult</code>.</p>
</div>

<div class="recipe">
<h4>Региональные формы (алольские, хисуйские, галарские&hellip;)</h4>
<p class="why">Очевидный подход через <code>label:</code> здесь не работает.</p>
<pre><code>  regionals:
    name: "Regional Forms"
    spec: "aspect:alolan|hisuian|galarian|paldean"
    color: "#F472B6"</code></pre>
<p><b>Почему:</b> <code>hisuian_form</code> и <code>galarian_form</code> объявлены на <b>формах</b>, а
<code>label:</code> читает <b>вид</b>. У алольской Vulpix метки вида остаются <code>gen1,
kantonian_form</code>. Алольской её делает <b>аспект</b>.</p>
</div>

<div class="recipe">
<h4>Оповещения об идеальных IV</h4>
<p class="why">Ради чего игроки на самом деле бегут за спавном.</p>
<pre><code>  flawless:
    name: "Flawless"
    spec: "perfectIvs:&gt;=5"
    color: "#FBBF24"
    max-stored: 20</code></pre>
<p>Используйте <code>ivs:&gt;=150</code> для «в целом отличных», а не «пять максимальных».</p>
</div>

<div class="recipe">
<h4>Гиганты и коротышки</h4>
<p class="why">Охота за размером — отдельная коллекционная игра.</p>
<pre><code>  xxl:
    name: "XXL"
    spec: "scale:&gt;1.2"
    color: "#F97316"
  xxs:
    name: "XXS"
    spec: "scale:&lt;0.8"
    color: "#60A5FA"</code></pre>
</div>

<div class="recipe">
<h4>Конкурентные характеры</h4>
<pre><code>  perfectmons:
    name: "Battle-Ready"
    spec: "perfectIvs:&gt;=4 nature:adamant|jolly|modest|timid"
    color: "#22D3EE"</code></pre>
</div>

<div class="recipe">
<h4>Ностальгический сервер только по 1 поколению</h4>
<pre><code>  kanto:
    name: "Kanto"
    spec: "label:gen1"
    color: "#EF4444"
    max-stored: 30</code></pre>
<p>Работает любая из <code>gen1</code>&hellip;<code>gen9</code>. <code>gen8a</code> — набор Legends: Arceus.</p>
</div>

<div class="recipe">
<h4>Детёныши и ископаемые</h4>
<pre><code>  babies:
    name: "Babies"
    spec: "label:baby"
    color: "#FDA4AF"
  fossils:
    name: "Fossils"
    spec: "label:fossil"
    color: "#A16207"</code></pre>
</div>

<div class="recipe">
<h4>Ночная «жуткая» категория</h4>
<p class="why">Контекстные токены превращают категорию в событие.</p>
<pre><code>  midnight:
    name: "Midnight"
    spec: "time:night biome:swamp|dark_forest"
    color: "#7C3AED"</code></pre>
</div>

<div class="recipe">
<h4>Охотники за грозой</h4>
<pre><code>  stormborn:
    name: "Stormborn"
    spec: "weather:thunder !isShiny"
    color: "#FACC15"</code></pre>
</div>

<div class="recipe">
<h4>Находки глубоко под землёй</h4>
<pre><code>  deepdark:
    name: "The Deep"
    spec: "y:&lt;0 biome:deep_dark"
    color: "#334155"</code></pre>
</div>

<div class="recipe">
<h4>Только Незер / Энд</h4>
<p class="why">Два способа — и они не одинаковы.</p>
<pre><code>  nether:
    name: "Nether"
    spec: "isLegendary"
    dimensions: "minecraft:the_nether"    # жёсткий фильтр, ограничивает и спавнер</code></pre>
<p>Или как часть спека, чтобы это сочеталось с остальными токенами:</p>
<pre><code>    spec: "isShiny dimension:minecraft:the_end"</code></pre>
<p><b>Разница:</b> <code>dimensions:</code> — это фильтр уровня категории, которому <b>подчиняется и
встроенный спавнер</b>: категория только для Незера никогда ничего не разместит в Оверворлде.
<code>dimension:</code> в спеке влияет только на сопоставление.</p>
</div>

<div class="recipe">
<h4>Шайни, но не те, до которых никому нет дела</h4>
<pre><code>  shinies:
    name: "Shinies"
    spec: "isShiny !species:magikarp|zubat|caterpie"
    color: "#55FF55"</code></pre>
<p>Ключ <code>blacklist:</code> делает то же самое и вдобавок запрещает спавнеру их создавать. Спек нужен,
когда исключение должно сочетаться с другими токенами.</p>
</div>

<div class="recipe">
<h4>Награда за поимку</h4>
<pre><code>  legendaries:
    on-catch-commands:
      - "give %player% cobblemon:rare_candy 5"
      - "say %player% поймал %species%!"
      - "eco give %player% 5000"</code></pre>
<p>Работает с любым плагином или модом, у которого есть консольная команда. Интеграция не нужна.</p>
</div>

<div class="recipe">
<h4>Публичный Discord для легендарных, стафф-канал для остального</h4>
<pre><code>notifications:
  legendaries:
    discord-webhook: "https://discord.com/api/webhooks/&lt;public&gt;"
    discord-template: "&#128142; **%species%** в %biome%!"
  shinies:
    discord-webhook: "https://discord.com/api/webhooks/&lt;staff&gt;"
    discord-template: "%species% на %x% %y% %z%"</code></pre>
</div>

<div class="recipe">
<h4>Тихий сервер</h4>
<p class="why">Некоторые сообщества ненавидят спам в чате, но хотят знать.</p>
<pre><code>  shinies:
    chat: ""
    actionbar: "&lt;gold&gt;%species% рядом&lt;/gold&gt;"
    sound-volume: 0</code></pre>
<p>Actionbar не зависит от <code>show-title</code>, так что это даёт подсказку без строки в чате, без
титульной карточки и без звука.</p>
</div>

<div class="recipe">
<h4>PvP-сервер: никогда не выдавать координаты</h4>
<pre><code>general-settings:
  hide-exact-position: true</code></pre>
<p>Округляет все координаты до центра чанка в чате, луче, вейпоинте, GUI истории и Discord. Операторы в
GUI по-прежнему видят точные — чтобы могли модерировать.</p>
<p>Сочетайте с радиусом, чтобы слышали только те, кто рядом:</p>
<pre><code>    play-to-all: false
    broadcast-radius: 150</code></pre>
</div>

<div class="recipe">
<h4>Событие на выходные: больше легендарных</h4>
<pre><code>    spawn:
      interval-ticks: 12000     # каждые 10 минут
      chance: 0.6
      player-cooldown-ticks: 0</code></pre>
<p><code>/ct reload</code> применяет на лету. В понедельник верните обратно.</p>
</div>

<div class="recipe">
<h4>У вас уже есть датапак с легендарными</h4>
<p class="why">Ничего не делайте.</p>
<pre><code>    spawn:
      mode: auto     # значение по умолчанию</code></pre>
<p>Директор обнаружит датапак и отступит. <code>/checklegendary</code> тогда покажет реальные записи и
шансы датапака. Если нужны <b>оба</b> источника, поставьте <code>mode: always</code>.</p>
</div>

<div class="recipe">
<h4>Объявлять всё редкое одной категорией</h4>
<pre><code>  rare:
    name: "Rare"
    spec: "isLegendary|isMythical"
    color: "#FF3333"</code></pre>
<p><b>Осторожно:</b> <code>|</code> работает внутри <i>значения</i> токена, а не между токенами. Строка
выше <b>невалидна</b>. Используйте две категории или общую для видов метку.</p>
</div>

<div class="recipe">
<h4>Отделить шайни-стартеров от обычных</h4>
<p class="why">Потому что выигрывает первое совпадение, а вам может быть нужно обратное умолчанию.</p>
<pre><code>  shinystarters:
    name: "Shiny Starters"
    spec: "label:starter isShiny"
    color: "#FDE047"
  starters:
    name: "Starters"
    spec: "label:starter !isShiny"
    color: "#4ADE80"</code></pre>
<p>Обе поставьте <b>выше</b> общей категории <code>shinies</code>.</p>
</div>
`
},

{
group: "Руководства", id: "troubleshooting", title: "Решение проблем",
lede: "Сначала симптом.",
body: `
<h3>Новая категория пишется в историю, но ничего не объявляет</h3>
<p>У неё нет блока в <code>announcements.yml</code>. Неизвестный id разрешается в «выключено», что гасит и
луч. Ключ должен точно совпадать с id категории.</p>

<h3>Категория ни с чем не совпадает</h3>
<p>Проверьте лог при запуске или после <code>/ct reload</code>:</p>
<pre><code>Tracker 'x' has an unusable spec ("..."): unknown spec token 'isfluffy'
  - it will not match anything until fixed</code></pre>
<p>Частые причины: опечатка в токене, нечитаемое булево (<code>isShiny:maybe</code>), пустая альтернатива
(<code>a||b</code>) или <code>label:mega</code> / <code>label:hisuian_form</code> — это метки уровня формы,
см. <a href="#labels">Метки</a>.</p>

<h3><code>/ct reload</code> говорит, что сохранил прежние настройки</h3>
<p>Один из YAML-файлов не распарсился. Действующая конфигурация намеренно оставлена нетронутой. В логе
указана строка. Частая причина — блок <code>trackers:</code> сдвинут на уровень вглубь: это по-прежнему
валидный YAML, просто все трекеры исчезают, и именно ради этого случая проверка существует.</p>

<h3>Настройки луча будто ничего не делают</h3>
<p>Они читаются в момент спавна. Измените их в <code>config.yml</code>, сделайте <code>/ct reload</code>, а
затем вызовите <b>новый</b> спавн. Именно поэтому их нет в <code>/ct admin</code>.</p>

<h3>Луч исчез, когда я улетел</h3>
<p>Ожидаемо, пока он вне зоны дольше окна grace. Если исчез сам покемон — это деспавнер Cobblemon, а не
CobbleTracker: дикие покемоны деспавнятся, когда рядом нет игрока. При тестах с перезаходом поднимите
<code>despawnerMinAgeTicks</code> в <code>config/cobblemon/main.json</code>, иначе вы будете проверять
деспавнер.</p>

<h3>/checklegendary пишет, что легендарных спавнов нет, но датапак установлен</h3>
<p>Датапак не подхватился. Чаще всего он рассчитан на старый Cobblemon: в 1.7 ключ спавна
<code>context</code> переименован в <code>spawnablePositionType</code>, и записи со старым именем
отбрасываются. Проверьте лог сервера при запуске на ошибки самого Cobblemon.</p>

<h3>/spawnlegendary отказывает</h3>
<p>Ни одно правило не подходит там, куда упал бы спавн. Это работа контекстной системы. Запустите
<code>/checklegendary</code>, чтобы увидеть, что допускает место, или перейдите в подходящий биом /
дождитесь погоды.</p>

<h3>Ссылка на вейпоинт пишет, что миникарта не найдена</h3>
<p>Клиент не сообщил о поддерживаемой миникарте. Проверьте, что она установлена на клиенте и что формат
не выключен на сервере в блоке <code>minimap:</code>.</p>

<h3>После установки LuckPerms все потеряли доступ к /ct</h3>
<p>Такого быть не должно — незаданная нода откатывается к умолчанию. Если это произошло, значит нода
где-то в цепочке наследования явно выставлена в <code>false</code>. Проверьте:
<code>/lp user &lt;имя&gt; permission check cobbletracker.command.gui</code>.</p>

<h3>Discord-вебхук ничего не публикует</h3>
<p>Ищите в логе <code>Discord webhook post failed</code>. URL никогда не логируется, поэтому проверьте его
вручную. Он должен быть <code>https://</code>. Учтите также, что у категории должно быть
<code>enabled: true</code>.</p>
`
},

{
group: "Руководства", id: "files", title: "Данные и файлы",
lede: "Что где лежит и что можно безопасно удалить.",
body: `
<table>
<tr><th>Файл</th><th>Сторона</th><th>Править руками?</th><th>Содержимое</th></tr>
<tr><td><code>config/cobbletracker/config.yml</code></td><td>сервер</td><td>да</td><td>Правила сервера, категории, спавнер, хуки</td></tr>
<tr><td><code>config/cobbletracker/announcements.yml</code></td><td>сервер</td><td>да</td><td>Тексты объявлений, звук, Discord</td></tr>
<tr><td><code>config/cobbletracker/legendaries.yml</code></td><td>сервер</td><td>да</td><td>Правила директора</td></tr>
<tr><td><code>config/cobbletracker/tracker.json</code></td><td>сервер</td><td>нет</td><td>История спавнов</td></tr>
<tr><td><code>config/cobbletracker/prefs.json</code></td><td>сервер</td><td>нет</td><td>Настройки уведомлений игроков</td></tr>
<tr><td><code>config/cobbletracker/hunt.json</code></td><td><b>клиент</b></td><td>нет</td><td>Ваш список видов для охоты</td></tr>
</table>

<h3>Удаление</h3>
<ul>
<li>Удалите YAML-файл — он будет создан заново с комментариями при следующем запуске.</li>
<li>Удалите <code>tracker.json</code>, чтобы стереть историю. Больше от него ничего не зависит.</li>
<li>Удалите <code>prefs.json</code>, чтобы сбросить всех игроков на «всё включено».</li>
</ul>

<h3>Устойчивость</h3>
<ul>
<li>JSON-файлы пишутся во временный файл и затем перемещаются, поэтому падение посреди записи оставляет
предыдущую копию целой.</li>
<li>Испорченный <code>tracker.json</code> пишет ошибку в лог и стартует пустым, а не роняет сервер.</li>
<li>YAML читается ограниченным парсером — конфиг никогда не сможет создать произвольные классы.</li>
<li>Записи под категорией, которую вы убрали из конфига, <b>сохраняются</b>, а не удаляются. Они просто не
показываются. Верните категорию — вернётся и её история.</li>
</ul>

<h3>Переход с 1.0.0</h3>
<p>Делать ничего не нужно. Старый <code>config.yml</code> загружается как есть, включая убранный ключ
<code>kind: block</code> — и такая категория теперь действительно работает, тогда как раньше молча
игнорировалась. У старых записей истории нет характеристик покемона, поэтому их карточки просто не
показывают эту строку.</p>
`
}

]};
