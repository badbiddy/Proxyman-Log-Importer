
import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Proxyman Log Importer");
        api.userInterface().registerSuiteTab("Proxyman Log Importer", new MainTab(api));
    }
}
