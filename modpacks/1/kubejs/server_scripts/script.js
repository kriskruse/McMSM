// priority: 0

onEvent('server.datapack.first', event => {
    
  const bee = (name, primaryColor, secondaryColor, flower) => {
    var data = {
      "primaryColor": primaryColor,
      "secondaryColor": secondaryColor,
      "createComb": true
    };
    if(flower.substring(0,1) == '#')
      data['flowerTag'] = flower.substring(1);
    else
      data['flowerBlock'] = flower;
    event.addJson('productivebees:productivebees/'+name+'.json', data);
  };


  //ars nouveau
  bee('mana', '#742D97', '#DA97FC', 'ars_nouveau:mana_gem_block');
  //astral sorcery
 // bee('astralsorcery/aquamarine', '#002563', '#00E7F3', '#minecraft:sand');
  //bee('astralsorcery/star_metal', '#090839', '#CBCFEF', 'astralsorcery:starmetal');
  bee('astralsorcery/marble', '#9B9B9B', '#DEDEDE', '#forge:marble');

  //extreme reactors
  bee('yellorium', '#E3D570', '#FCF88F', '#forge:storage_blocks/yellorium');

  //aquaculture
  bee('neptunium', '#07A177', '#17F1B6', '#forge:storage_blocks/neptunium');

  //eidolon
  bee('arcane_gold', '#9F4E37', '#F4CE7B', '#forge:storage_blocks/arcane_gold');
  bee('pewter', '#46463D', '#9F9E95', '#forge:storage_blocks/pewter');


  /*
  const silents = (name, primaryColor, secondaryColor) => {
        bee('gems/'+name, primaryColor, secondaryColor, '#forge:storage_blocks/'+name);
  }
  silents('ruby', '#C42415', '#FAB5AD');
  silents('garnet', '#A80A00', '#F06040');
  silents('topaz', '#A84200', '#DA7C17');
  silents('amber', '#CD6200', '#F9C902');
  silents('heliodor', '#DACD17', '#F1E66D');
  silents('peridot', '#81CA16', '#BAF16D');
  silents('green_sapphire', '#0B7A00', '#6DF178');
  silents('phosphophyllite', '#16CA7C', '#6DF1BA');
  //silents('aquamarine', '#009392', '#6DE6F1');
  silents('sapphire', '#000B7A', '#786DF1');
  silents('tanzanite', '#5E16CA', '#8B40F0');
  silents('amethyst', '#6903C5', '#924CD0');
  silents('agate', '#6F0B7D', '#BE1388');
  silents('morganite', '#D3798F', '#FC8FA2');
  silents('onyx', '#070707', '#373737');
  silents('opal', '#B5B5B5', '#FCFCFC');
  silents('carnelian', '#4B0600', '#CE4302');
  silents('spinel', '#881700', '#DE9D6C');
  silents('citrine', '#543100', '#965800');

  silents('jasper', '#C65403', '#917A00');
  silents('zircon', '#454500', '#CFD955');
  silents('moldavite', '#283600', '#919700');
  silents('malachite', '#002B0C', '#22D645');
  silents('turquoise', '#002B22', '#24D7AA');
  silents('euclase', '#001B27', '#70C5E2');
  silents('benitoite', '#000C45', '#001BB4');
  silents('iolite', '#0F0054', '#6F00BB');
  silents('alexandrite', '#250027', '#C818C6');
  silents('lepidolite', '#500045', '#D240B1');
  silents('ametrine', '#54002A', '#97004F');
  silents('black_diamond', '#141414', '#818181');
  silents('moonstone', '#999999', '#FCFCFC');
  silents('pyrope', '#8C133C', '#FC405D');
  silents('coral', '#FC2338', '#FC722E');
  silents('sunstone', '#532F27', '#FC684B');
  silents('cats_eye', '#D28E41', '#FCC96A');
  silents('yellow_diamond', '#8F981C', '#EAD649');
  silents('jade', '#7E9E45', '#D3FC74');
  silents('chrysoprase', '#67E42F', '#28F6C4');
  silents('apatite', '#27CFA2', '#68FCFC');
  silents('fluorite', '#1E454B', '#46DEF1');
  silents('kyanite', '#092535', '#406CFC');
  silents('sodalite', '#23379C', '#4747FB');
  silents('ammolite', '#9618B0', '#2A7A00');
  silents('kunzite', '#381844', '#FB47EC');
  silents('rose_quartz', '#FC6ABB', '#FCBDF8');
  silents('tektite', '#3D2B1E', '#A39383');
  silents('pearl', '#7B93BC', '#F1F3F7');*/
})

onEvent('world.load', event => {
  var FTBChunksWorldConfig = java('dev.ftb.mods.ftbchunks.FTBChunksWorldConfig');
  FTBChunksWorldConfig.MAX_FORCE_LOADED_CHUNKS.set(0);

})
