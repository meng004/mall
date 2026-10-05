"""Read-only review probes; compile outside the repo using existing test classpaths."""
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

root = Path(sys.argv[1]).resolve()
jdk = Path(sys.argv[2]) / "bin"
sources = Path(__file__).resolve().parent
reports = {
    "SpecProbe": "mall-admin/target/surefire-reports/TEST-com.macro.mall.sdc.SDCE304Test.xml",
    "CouponProbe": "SDC/E3/target/surefire-reports/TEST-com.macro.mall.sdc.e3.SDCE301Test.xml",
    "SdcAttributeProbe": "SDC/E7/target/surefire-reports/TEST-com.macro.mall.sdc.e7.SDCE705Test.xml",
}
for name, report in reports.items():
    properties = ET.parse(root / report).getroot().find("properties")
    classpath = next(p.get("value") for p in properties if p.get("name") == "java.class.path")
    with tempfile.TemporaryDirectory(prefix="sdc-review-") as directory:
        subprocess.run([str(jdk / "javac"), "-cp", classpath, "-d", directory,
                        str(sources / (name + ".java"))], check=True)
        subprocess.run([str(jdk / "java"), "-cp", directory + ":" + classpath, name,
                        str(root)], check=True)
