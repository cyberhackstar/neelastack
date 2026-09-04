import { bootstrapApplication, type BootstrapContext } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { config } from './app/app.config.server';

// Angular 19.2 changed the SSR bootstrap contract: the exported function must accept
// and forward a BootstrapContext, or the SSR route-analysis tooling (which calls this
// during dev-server and build) fails with "NG0401: Missing Platform" — the pre-19.2
// two-argument form silently no longer works once the installed CLI/core is >=19.2.
const bootstrap = (context: BootstrapContext) => bootstrapApplication(AppComponent, config, context);

export default bootstrap;
