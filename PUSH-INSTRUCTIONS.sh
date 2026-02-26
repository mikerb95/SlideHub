#!/bin/bash
# ════════════════════════════════════════════════════════════════════════════
# SlideHub — Instrucciones de Validación y Push de Fase 2
# ════════════════════════════════════════════════════════════════════════════
#
# USO:
#   1. Lee este archivo de principio a fin
#   2. Ejecuta los comandos paso por paso en tu terminal
#   3. Válida que Aiven funciona ANTES de hacer push
#
# TIEMPO ESTIMADO: 5-10 minutos
# ════════════════════════════════════════════════════════════════════════════

echo ""
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║  SlideHub — Validación de Aiven & Push de Fase 2                 ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# ════════════════════════════════════════════════════════════════════════════
# PASO 1: Validar que estás en el directorio correcto
# ════════════════════════════════════════════════════════════════════════════

echo "📍 PASO 1: Validar ubicación"
echo "────────────────────────────────────────────────────────────────────"

if [ -f "docker-compose.yml" ] && [ -f "pom.xml" ]; then
    echo "✅ Estás en la carpeta correcta: $(pwd)"
else
    echo "❌ ERROR: No estás en /home/mike/dev/learning/SlideHub"
    echo "   Ve a: cd /home/mike/dev/learning/SlideHub"
    exit 1
fi
echo ""

# ════════════════════════════════════════════════════════════════════════════
# PASO 2: Validar cambios locales
# ════════════════════════════════════════════════════════════════════════════

echo "📝 PASO 2: Verificar archivos modificados"
echo "────────────────────────────────────────────────────────────────────"

echo "Archivos que serán modificados en el commit:"
echo ""
echo "  ✏️  slidehub-core/src/main/resources/application.yml"
echo "  ✏️  slidehub-gateway/src/main/java/com/slidehub/gateway/config/GatewayConfig.java"
echo "  ✏️  docker-compose.yml"
echo "  ✏️  .env.example"
echo "  🆕 AIVEN-SETUP.md"
echo "  🆕 VALIDATE-AIVEN.md"
echo "  🆕 PHASE-2-SUMMARY.md"
echo "  🆕 test-aiven-connection.sh"
echo ""

read -p "¿Ves estos archivos en tu git? (s/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Ss]$ ]]; then
    echo "⚠️  Alguno de los archivos podría no estarse rastreando"
    echo "   Verifica con: git status"
fi
echo ""

# ════════════════════════════════════════════════════════════════════════════
# PASO 3: VALIDAR AIVEN ANTES DE PUSH
# ════════════════════════════════════════════════════════════════════════════

echo "🔐 PASO 3: VALIDAR CONEXIÓN A AIVEN ⚠️  IMPORTANTE"
echo "────────────────────────────────────────────────────────────────────"
echo ""
echo "ANTES de hacer push, DEBES validar que Aiven funciona."
echo ""
echo "Sigue estos pasos:"
echo ""
echo "  1. Abre VALIDATE-AIVEN.md en tu editor:"
echo "     cat VALIDATE-AIVEN.md"
echo ""
echo "  2. O sigue estos comandos rápidos:"
echo ""
echo "     # Conectar a Aiven (te pedirá password)"
echo "     psql -h slidelat-bd-slidelat.i.aivencloud.com \\"
echo "          -p 21552 \\"
echo "          -U avnadmin \\"
echo "          -d defaultdb"
echo ""
echo "     # Dentro de psql, ejecuta:"
echo "     SELECT version();"
echo "     \\q  (para salir)"
echo ""
echo "  3. Si ves la versión de PostgreSQL → ✅ AIVEN FUNCIONA"
echo "     Si hay error de password → ❌ Verifica password en Aiven"
echo ""

read -p "¿Ya validaste que Aiven funciona? (s/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Ss]$ ]]; then
    echo "⚠️  DEBES validar Aiven PRIMERO"
    echo "   Ejecuta: psql -h slidelat-bd-slidelat.i.aivencloud.com -p 21552 -U avnadmin -d defaultdb"
    exit 1
fi
echo ""
echo "✅ Aiven validado. Continuamos con el push."
echo ""

# ════════════════════════════════════════════════════════════════════════════
# PASO 4: Compilar para verificar que todo está bien
# ════════════════════════════════════════════════════════════════════════════

echo "🔨 PASO 4: Compilar proyecto (verificación)"
echo "────────────────────────────────────────────────────────────────────"
echo ""

read -p "¿Compilar proyecto para verificar? (s/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Ss]$ ]]; then
    echo "Compilando (esto toma ~30 segundos)..."
    ./mvnw clean compile -DskipTests -q
    
    if [ $? -eq 0 ]; then
        echo "✅ Compilación exitosa"
    else
        echo "❌ Error en compilación"
        echo "   Revisa los cambios antes de hacer push"
        exit 1
    fi
fi
echo ""

# ════════════════════════════════════════════════════════════════════════════
# PASO 5: Hacer el push
# ════════════════════════════════════════════════════════════════════════════

echo "📤 PASO 5: PUSH A GITHUB"
echo "────────────────────────────────────────────────────────────────────"
echo ""
echo "Comando a ejecutar:"
echo ""
echo "  git add slidehub-core/src/main/resources/application.yml"
echo "  git add slidehub-gateway/src/main/java/com/slidehub/gateway/config/GatewayConfig.java"
echo "  git add docker-compose.yml"
echo "  git add .env.example"
echo "  git add AIVEN-SETUP.md"
echo "  git add VALIDATE-AIVEN.md"
echo "  git add PHASE-2-SUMMARY.md"
echo "  git add test-aiven-connection.sh"
echo "  git add slidehub-*/pom.xml pom.xml"
echo ""
echo "  git commit -m \"Fase 2: PostgreSQL en Aiven, sin Eureka, preparado para IA\""
echo ""
echo "  git push origin main"
echo ""

read -p "¿Hacer push ahora? (s/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Ss]$ ]]; then
    echo "Agregando archivos al stage..."
    git add slidehub-core/src/main/resources/application.yml
    git add slidehub-gateway/src/main/java/com/slidehub/gateway/config/GatewayConfig.java
    git add docker-compose.yml
    git add .env.example
    git add AIVEN-SETUP.md
    git add VALIDATE-AIVEN.md
    git add PHASE-2-SUMMARY.md
    git add test-aiven-connection.sh
    git add slidehub-*/pom.xml pom.xml
    
    echo ""
    echo "Creando commit..."
    git commit -m "Fase 2: PostgreSQL en Aiven, sin Eureka, preparado para IA"
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "Push a GitHub..."
        git push origin main
        
        if [ $? -eq 0 ]; then
            echo ""
            echo "╔═══════════════════════════════════════════════════════╗"
            echo "║  ✅ PUSH EXITOSO                                      ║"
            echo "║                                                       ║"
            echo "║  Cambios subidos a main branch                       ║"
            echo "║  GitHub le notificará a Render que hay cambios       ║"
            echo "╚═══════════════════════════════════════════════════════╝"
        else
            echo ""
            echo "❌ Error en push"
            echo "   Verifica: git log para ver commits locales"
            exit 1
        fi
    else
        echo ""
        echo "❌ Error en commit"
        echo "   Verifica: git status"
        exit 1
    fi
else
    echo "⏭️  Push cancelado"
    echo "   Puedes hacerlo después manualmente"
fi

echo ""
echo "════════════════════════════════════════════════════════════════════"
echo "✨ Fase 2 completada. Próximo: Fase 3 (MongoDB + Gemini)"
echo "════════════════════════════════════════════════════════════════════"
echo ""
