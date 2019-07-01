import {APIModel} from "./tracing-results";

export interface Config {
  libraryModuleName: string;
  output?: string;
  regressionInfo: APIModel|null;
  enableUnifications?: boolean;
  collectStackTraces: boolean;
  detailedStackTraces: boolean;
  collectOwnProperties: boolean;
  withUnifications: boolean;
  testDistillationMode: boolean;
  blacklistSideEffects: boolean;
  coverage: boolean;
  libraryVersion: string;
}
