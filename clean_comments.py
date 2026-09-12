import re, os, glob

# dosya adına göre üste yazılacak açıklama
descriptions = {
    "IGIFPlugin.java":                  "Plugin giriş noktası. Her şey buradan başlar.",
    "AnimationConfig.java":             "Bir animasyonun config.yml dosyasından okunan ayarları.",
    "Animation.java":                   "Bellekteki animasyon nesnesi. Frame ID'lerini ve config'i tutar.",
    "AnimationLoader.java":             "Disk üzerindeki animasyon klasörlerini tarar, yükler ve hafızada saklar.",
    "DisplayType.java":                 "Animasyonun nerede gösterileceği: title, subtitle veya actionbar.",
    "GifFrame.java":                    "Bir GIF'ten çıkarılmış tek bir kare.",
    "GifFrameExtractor.java":           "GIF dosyasını okur, her kareyi ayrı ayrı çıkarır ve disposal compositing uygular.",
    "AnimationProcessor.java":          "GIF → PNG kareler → ItemsAdder asset pipeline'ını yönetir.",
    "PlaybackSession.java":             "Bir oyuncu için tek bir animasyon oynatma oturumu.",
    "PlaybackManager.java":             "Tüm aktif oynatma oturumlarını yönetir, disconnect'leri temizler.",
    "ItemsAdderIntegration.java":       "ItemsAdder ile konuşur: asset üretir, texture kopyalar, reload tetikler.",
    "SkriptIntegration.java":           "Opsiyonel Skript desteği. Skript yoksa hiçbir şey patlamaz.",
    "iGIFAPI.java":                     "Dış pluginler için public API arayüzü.",
    "iGIFAPIImpl.java":                 "Public API'nin gerçek implementasyonu.",
    "IGIFAnimationStartEvent.java":     "Bir animasyon oynatılmaya başlandığında fırlatan event.",
    "IGIFAnimationStopEvent.java":      "Bir animasyon durdurulduğunda fırlatan event.",
    "IGIFAnimationGeneratedEvent.java": "Bir animasyon generate edildiğinde fırlatan event.",
    "ConsoleLogger.java":               "Renkli ANSI destekli konsol logger.",
    "MessageService.java":              "messages.yml'yi yükler, MiniMessage ile formatlar, placeholder'ları doldurur.",
    "IGIFCommand.java":                 "/igif komutunun tüm subcommand'leri ve tab completion.",
}

def remove_java_comments(code):
    # Block comments /* ... */
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)
    # Single-line comments, string-aware
    result = []
    i = 0
    n = len(code)
    in_string = False
    in_char = False
    while i < n:
        c = code[i]
        if in_string:
            result.append(c)
            if c == '\\' and i + 1 < n:
                result.append(code[i + 1])
                i += 2
                continue
            if c == '"':
                in_string = False
        elif in_char:
            result.append(c)
            if c == '\\' and i + 1 < n:
                result.append(code[i + 1])
                i += 2
                continue
            if c == "'":
                in_char = False
        else:
            if c == '"':
                in_string = True
                result.append(c)
            elif c == "'":
                in_char = True
                result.append(c)
            elif code[i:i+2] == '//':
                while i < n and code[i] != '\n':
                    i += 1
                continue
            else:
                result.append(c)
        i += 1
    return ''.join(result)

def clean_blank_lines(code):
    # 3'ten fazla arka arkaya boş satırı 2'ye indir
    return re.sub(r'\n{3,}', '\n\n', code)

root = r'c:\Users\fkutu\Documents\igif\src\main\java'
files = glob.glob(os.path.join(root, '**', '*.java'), recursive=True)

for path in files:
    fname = os.path.basename(path)
    with open(path, 'r', encoding='utf-8') as f:
        code = f.read()

    code = remove_java_comments(code)
    code = clean_blank_lines(code)
    code = code.strip()

    desc = descriptions.get(fname, fname.replace('.java', '') + " sınıfı.")
    code = f"// {desc}\n{code}\n"

    with open(path, 'w', encoding='utf-8') as f:
        f.write(code)
    print(f"cleaned: {fname}")

print("Bitti.")