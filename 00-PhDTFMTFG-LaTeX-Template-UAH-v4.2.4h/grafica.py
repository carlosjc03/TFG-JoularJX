# Requiere matplotlib:  pip install matplotlib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

# Datos de Pereira et al. (2017), energía normalizada a C = 1.00
langs  = ["C", "Rust", "C++", "Java", "Go", "JavaScript", "PHP", "Python"]
vals   = [1.00, 1.03, 1.34, 1.98, 3.23, 4.45, 29.30, 75.88]

colors = ["#9aa4b2"] * len(langs)
colors[langs.index("Java")]   = "#2563eb"   # Java en azul
colors[langs.index("Python")] = "#dc2626"   # Python en rojo

fig, ax = plt.subplots(figsize=(7.2, 4.0))
bars = ax.bar(langs, vals, color=colors, edgecolor="#334155", linewidth=0.6)
ax.set_yscale("log")
ax.set_ylabel("Energía relativa (C = 1)")
ax.set_title("Consumo energético por lenguaje ejecutando las mismas tareas")
ax.set_axisbelow(True)
ax.grid(axis="y", color="#e2e8f0", linewidth=0.7)
for b, v in zip(bars, vals):
    ax.text(b.get_x() + b.get_width()/2, v*1.08, f"{v:g}×",
            ha="center", va="bottom", fontsize=9)
ax.set_ylim(0.8, 130)
for s in ["top", "right"]:
    ax.spines[s].set_visible(False)
plt.tight_layout()

# Guardar SIN metadatos
plt.savefig("consumo_lenguajes.png", dpi=200, bbox_inches="tight",
            metadata={"Software": None})
print("Imagen generada: consumo_lenguajes.png")