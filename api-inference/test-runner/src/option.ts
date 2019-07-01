export interface Options {
  modelFile: string;
  libraryFolder: string;
  skipHigherOrder: string;
  exactValueChecking: string;
  libraryVersion?: string;
  libraryName?: string;
  coverage?: string;
}

export class OptionsOps {
  static toString(o: Options): string { return JSON.stringify(o, null, 2); }
}