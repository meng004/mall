"""Compile review-only probes against the current E7 test classpath."""
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

root = Path(sys.argv[1]).resolve()
jdk = Path(sys.argv[2]) / "bin"
source = Path(__file__).resolve().parent
report = root / "SDC/E7/target/surefire-reports/TEST-com.macro.mall.sdc.e7.CandidateEvaluationTest.xml"
properties = ET.parse(report).getroot().find("properties")
classpath = next(p.get("value") for p in properties if p.get("name") == "java.class.path")
for name, main in [("ReviewScoreProbe", "com.macro.mall.sdc.e7.ReviewScoreProbe"),
                   ("OrderFactsProbe", "OrderFactsProbe")]:
    with tempfile.TemporaryDirectory(prefix="sdc-review2-") as directory:
        subprocess.run([str(jdk / "javac"), "-cp", classpath, "-d", directory,
                        str(source / (name + ".java"))], check=True)
        subprocess.run([str(jdk / "java"), "-cp", directory + ":" + classpath, main], check=True)
