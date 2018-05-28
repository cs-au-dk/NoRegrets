
import {Learned} from "./tracing-results";

export interface Config {
    libraryModuleName: string;
    output?: string;
    regressionInfo: Learned | null;
    enableUnifications?: boolean;
    collectStackTraces: boolean;
    detailedStackTraces: boolean;
    collectOwnProperties: boolean;
    withUnifications: boolean;
}
