function fn() {
    var baseUrl = karate.properties['baseUrl'];
    if (!baseUrl) throw 'baseUrl must be supplied by the container test runner';
    karate.configure('connectTimeout', 5000);
    karate.configure('readTimeout', 10000);
    return {baseUrl: baseUrl};
}
