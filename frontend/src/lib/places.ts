/** Offline catalogue of popular travel cities grouped by country (ISO 3166-1 alpha-2), most popular first. */
export const COUNTRIES: { code: string; name: string; aliases?: string[]; cities: string[] }[] = [
  {
    code: "IN",
    name: "India",
    aliases: ["Bharat"],
    cities: [
      "Goa", "Mumbai", "Delhi", "Jaipur", "Agra", "Bengaluru", "Pune", "Hyderabad", "Chennai", "Kolkata", "Udaipur",
      "Manali", "Shimla", "Rishikesh", "Varanasi", "Leh", "Srinagar", "Darjeeling", "Munnar", "Kochi", "Alleppey",
      "Ooty", "Mysuru", "Hampi", "Pondicherry", "Jodhpur", "Jaisalmer", "Amritsar", "Gangtok", "Shillong",
      "Ahmedabad", "Lonavala", "Mahabaleshwar", "Nashik", "Aurangabad", "Coorg", "Kodaikanal", "Madurai",
      "Rameswaram", "Andaman (Port Blair)", "Nainital", "Mussoorie", "Dharamshala", "Haridwar", "Khajuraho",
      "Bhopal", "Indore", "Lucknow", "Chandigarh", "Visakhapatnam", "Bhubaneswar", "Puri", "Guwahati", "Kanyakumari",
      "Gokarna", "Alibaug", "Ratnagiri", "Wayanad", "Varkala", "Spiti",
    ],
  },
  {
    code: "TH",
    name: "Thailand",
    cities: ["Bangkok", "Phuket", "Chiang Mai", "Pattaya", "Krabi", "Koh Samui", "Ayutthaya", "Chiang Rai", "Hua Hin", "Koh Phi Phi"],
  },
  { code: "AE", name: "United Arab Emirates", aliases: ["UAE", "Emirates"], cities: ["Dubai", "Abu Dhabi", "Sharjah", "Ras Al Khaimah", "Fujairah", "Ajman"] },
  { code: "SG", name: "Singapore", cities: ["Singapore"] },
  { code: "MY", name: "Malaysia", cities: ["Kuala Lumpur", "Langkawi", "Penang", "Malacca", "Kota Kinabalu", "Cameron Highlands"] },
  { code: "ID", name: "Indonesia", cities: ["Bali", "Jakarta", "Yogyakarta", "Lombok", "Ubud", "Komodo", "Bandung"] },
  { code: "VN", name: "Vietnam", cities: ["Hanoi", "Ho Chi Minh City", "Da Nang", "Hoi An", "Ha Long Bay", "Nha Trang", "Hue", "Sapa", "Phu Quoc"] },
  { code: "LK", name: "Sri Lanka", cities: ["Colombo", "Kandy", "Galle", "Ella", "Sigiriya", "Nuwara Eliya", "Bentota", "Mirissa"] },
  { code: "NP", name: "Nepal", cities: ["Kathmandu", "Pokhara", "Chitwan", "Lumbini", "Nagarkot", "Bhaktapur"] },
  { code: "BT", name: "Bhutan", cities: ["Thimphu", "Paro", "Punakha", "Bumthang"] },
  { code: "MV", name: "Maldives", cities: ["Malé", "Maafushi", "Addu City"] },
  { code: "JP", name: "Japan", cities: ["Tokyo", "Kyoto", "Osaka", "Hiroshima", "Nara", "Sapporo", "Hakone", "Fukuoka", "Okinawa", "Nagoya"] },
  { code: "KR", name: "South Korea", aliases: ["Korea"], cities: ["Seoul", "Busan", "Jeju", "Incheon", "Gyeongju"] },
  { code: "CN", name: "China", cities: ["Beijing", "Shanghai", "Hong Kong", "Xi'an", "Guilin", "Chengdu", "Hangzhou", "Shenzhen"] },
  { code: "PH", name: "Philippines", cities: ["Manila", "Boracay", "Cebu", "Palawan", "El Nido", "Bohol"] },
  { code: "KH", name: "Cambodia", cities: ["Siem Reap", "Phnom Penh", "Sihanoukville", "Kampot"] },
  { code: "TR", name: "Turkey", aliases: ["Türkiye"], cities: ["Istanbul", "Cappadocia", "Antalya", "Izmir", "Bodrum", "Pamukkale", "Ankara"] },
  { code: "EG", name: "Egypt", cities: ["Cairo", "Luxor", "Aswan", "Sharm El Sheikh", "Hurghada", "Alexandria"] },
  { code: "GB", name: "United Kingdom", aliases: ["UK", "England", "Britain", "Scotland"], cities: ["London", "Edinburgh", "Manchester", "Liverpool", "Oxford", "Cambridge", "Bath", "York", "Glasgow"] },
  { code: "FR", name: "France", cities: ["Paris", "Nice", "Lyon", "Marseille", "Bordeaux", "Strasbourg", "Cannes", "Mont-Saint-Michel"] },
  { code: "IT", name: "Italy", cities: ["Rome", "Venice", "Florence", "Milan", "Naples", "Amalfi", "Pisa", "Cinque Terre", "Lake Como"] },
  { code: "ES", name: "Spain", cities: ["Barcelona", "Madrid", "Seville", "Granada", "Valencia", "Ibiza", "Malaga", "Mallorca"] },
  { code: "PT", name: "Portugal", cities: ["Lisbon", "Porto", "Lagos", "Sintra", "Madeira", "Faro"] },
  { code: "DE", name: "Germany", cities: ["Berlin", "Munich", "Frankfurt", "Hamburg", "Cologne", "Heidelberg", "Dresden"] },
  { code: "NL", name: "Netherlands", aliases: ["Holland"], cities: ["Amsterdam", "Rotterdam", "The Hague", "Utrecht"] },
  { code: "CH", name: "Switzerland", cities: ["Zurich", "Geneva", "Lucerne", "Interlaken", "Zermatt", "Bern"] },
  { code: "AT", name: "Austria", cities: ["Vienna", "Salzburg", "Innsbruck", "Hallstatt"] },
  { code: "CZ", name: "Czech Republic", aliases: ["Czechia"], cities: ["Prague", "Cesky Krumlov", "Brno"] },
  { code: "GR", name: "Greece", cities: ["Athens", "Santorini", "Mykonos", "Crete", "Rhodes", "Thessaloniki"] },
  { code: "HR", name: "Croatia", cities: ["Dubrovnik", "Split", "Zagreb", "Hvar"] },
  { code: "HU", name: "Hungary", cities: ["Budapest"] },
  { code: "IS", name: "Iceland", cities: ["Reykjavik", "Akureyri", "Vik"] },
  { code: "NO", name: "Norway", cities: ["Oslo", "Bergen", "Tromsø", "Lofoten"] },
  { code: "SE", name: "Sweden", cities: ["Stockholm", "Gothenburg", "Kiruna"] },
  { code: "FI", name: "Finland", cities: ["Helsinki", "Rovaniemi"] },
  { code: "DK", name: "Denmark", cities: ["Copenhagen", "Aarhus"] },
  { code: "IE", name: "Ireland", cities: ["Dublin", "Galway", "Cork", "Killarney"] },
  { code: "RU", name: "Russia", cities: ["Moscow", "Saint Petersburg"] },
  { code: "US", name: "United States", aliases: ["USA", "America", "US"], cities: ["New York", "Los Angeles", "Las Vegas", "San Francisco", "Orlando", "Miami", "Chicago", "Washington DC", "Honolulu", "Seattle", "Boston"] },
  { code: "CA", name: "Canada", cities: ["Toronto", "Vancouver", "Montreal", "Banff", "Quebec City", "Niagara Falls"] },
  { code: "MX", name: "Mexico", cities: ["Cancún", "Mexico City", "Tulum", "Playa del Carmen", "Oaxaca"] },
  { code: "BR", name: "Brazil", cities: ["Rio de Janeiro", "São Paulo", "Salvador", "Florianópolis"] },
  { code: "AR", name: "Argentina", cities: ["Buenos Aires", "Mendoza", "Bariloche", "Ushuaia"] },
  { code: "PE", name: "Peru", cities: ["Cusco", "Lima", "Machu Picchu", "Arequipa"] },
  { code: "AU", name: "Australia", cities: ["Sydney", "Melbourne", "Brisbane", "Gold Coast", "Cairns", "Perth", "Adelaide"] },
  { code: "NZ", name: "New Zealand", cities: ["Auckland", "Queenstown", "Wellington", "Christchurch", "Rotorua"] },
  { code: "ZA", name: "South Africa", cities: ["Cape Town", "Johannesburg", "Durban", "Kruger National Park"] },
  { code: "KE", name: "Kenya", cities: ["Nairobi", "Maasai Mara", "Mombasa"] },
  { code: "TZ", name: "Tanzania", cities: ["Zanzibar", "Arusha", "Serengeti", "Dar es Salaam"] },
  { code: "MA", name: "Morocco", cities: ["Marrakech", "Casablanca", "Fes", "Chefchaouen"] },
  { code: "MU", name: "Mauritius", cities: ["Port Louis", "Grand Baie", "Flic en Flac"] },
  { code: "SC", name: "Seychelles", cities: ["Mahé", "Praslin", "La Digue"] },
  { code: "QA", name: "Qatar", cities: ["Doha"] },
  { code: "OM", name: "Oman", cities: ["Muscat", "Salalah", "Nizwa"] },
  { code: "SA", name: "Saudi Arabia", cities: ["Riyadh", "Jeddah", "AlUla", "Mecca", "Medina"] },
  { code: "JO", name: "Jordan", cities: ["Amman", "Petra", "Wadi Rum", "Aqaba"] },
  { code: "IL", name: "Israel", cities: ["Jerusalem", "Tel Aviv"] },
  { code: "AZ", name: "Azerbaijan", cities: ["Baku", "Gabala"] },
  { code: "GE", name: "Georgia", cities: ["Tbilisi", "Batumi", "Kazbegi"] },
  { code: "KZ", name: "Kazakhstan", cities: ["Almaty", "Astana"] },
];

export type PlaceSuggestion = { city: string; countryCode: string; country: string; hint?: string };

export const countryName = (code: string) => COUNTRIES.find((c) => c.code === code)?.name ?? code;

export function popularPlaces(limit = 8): PlaceSuggestion[] {
  return COUNTRIES.flatMap((country) =>
    country.cities.map((city) => ({ city, countryCode: country.code, country: country.name })),
  ).slice(0, limit);
}

const norm = (s: string) =>
  s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();

/**
 * Predictive place search. City prefix matches rank first, then word-prefix and substring matches;
 * when the query names a country (or its code/alias), that country's popular cities are listed.
 */
export function searchPlaces(query: string, limit = 8): PlaceSuggestion[] {
  const q = norm(query);
  if (!q) return [];
  const scored: { s: PlaceSuggestion; score: number; rank: number }[] = [];
  for (const c of COUNTRIES) {
    const countryKeys = [c.name, ...(c.aliases ?? [])].map(norm);
    const countryHit =
      norm(c.code) === q || countryKeys.some((k) => k.startsWith(q) || (q.length >= 4 && q.startsWith(k)));
    c.cities.forEach((city, rank) => {
      const n = norm(city);
      let score = 0;
      if (n === q) score = 100;
      else if (n.startsWith(q)) score = 80;
      else if (n.split(/[\s\-()]+/).some((w) => w.startsWith(q))) score = 60;
      else if (q.length >= 3 && n.includes(q)) score = 40;
      else if (countryHit) score = 30;
      if (score) scored.push({ s: { city, countryCode: c.code, country: c.name }, score, rank });
    });
  }
  scored.sort((a, b) => b.score - a.score || a.rank - b.rank);
  return scored.slice(0, limit).map((x) => x.s);
}
