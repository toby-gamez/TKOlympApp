import { getIntrospectionQuery } from "graphql";
process.stdout.write(JSON.stringify({ query: getIntrospectionQuery({ inputValueDeprecation: true }) }));
